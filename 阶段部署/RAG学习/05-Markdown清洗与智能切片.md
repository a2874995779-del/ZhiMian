# 05：Markdown 清洗与智能切片

> 对应智面 RAG 阶段任务：`6.6.2` 的第二部分。
>
> 项目路径：`E:\java-code\ZhiMian`
>
> 前置条件：03 的 Embedding/Redis Stack 最小闭环已经跑通，04 的 `knowledge_document`、`knowledge_chunk`、Mapper、状态机和测试已经完成。
>
> 本阶段学习方式：你按照文档亲手编写切片代码和单元测试，完成后交给我 Code Review。本阶段不写 Controller，不上传文件，不访问 MySQL，不调用 Embedding API，也不写入 Redis。
>
> 如果你对字符转换、换行、转义、`StringUtils`、正则和文本格式化不熟悉，先学习：[专题01-Java字符串、字符编码与文本处理.md](./专题01-Java字符串、字符编码与文本处理.md)。

---

## 一、本阶段到底解决什么问题

04 阶段已经能够保存一整份知识文档，但一整篇文档不能直接作为一个向量。

假设管理员导入一篇两万字的资料：

```text
Java并发编程完整指南.md
```

里面同时包含：

- 线程的生命周期；
- `synchronized`；
- `volatile`；
- CAS；
- AQS；
- 线程池；
- 拒绝策略；
- 并发故障排查。

如果把整篇文档生成一个向量，这个向量只能表达“这是一篇 Java 并发资料”，却很难精确表达“CallerRunsPolicy 为什么能形成反压”。当用户询问拒绝策略时，系统可能找不到最准确的局部内容。

因此，需要把文档转换成多个语义相对完整的小片段：

```text
完整文档
    ↓ 清洗
格式稳定的文本
    ↓ 识别 Markdown 标题
多个语义章节
    ↓ 对超长章节按 Token 再切分
多个大小合适且带少量重叠的 chunk
    ↓ 生成索引、路径、Hash、Token 数
KnowledgeChunkDraft 列表
```

本阶段最终只实现一个纯函数式能力：

```java
List<KnowledgeChunkDraft> chunk(
        String documentTitle,
        String sourceType,
        String content
);
```

它的输入是一份文档，输出是一组“待保存的知识片段”。它不关心文档 ID，不操作数据库，也不生成向量。

---

## 二、先建立正确的切片认知

### 2.1 Chunk 不是随便截取的一段字符串

好的 chunk 应同时满足四个目标：

1. **语义完整**：尽量不要把定义、原因和结论拆散。
2. **大小受控**：不能超过 Embedding 模型能稳定处理的范围。
3. **上下文明确**：单独拿出来时仍知道它属于哪个主题。
4. **结果稳定**：相同输入和相同配置必须产生相同切片。

例如原文：

```markdown
# Java 并发

## 线程池

### 拒绝策略

CallerRunsPolicy 会让提交任务的线程自己执行任务，
从而降低任务提交速度，形成一种自然的反压。
```

只保存正文：

```text
CallerRunsPolicy 会让提交任务的线程自己执行任务……
```

语义并不完整，因为检索结果看不出它属于：

```text
Java 并发 > 线程池 > 拒绝策略
```

因此每个 chunk 除了正文，还必须保留 `headingPath`。

### 2.2 为什么不能按固定字符数切

最粗糙的方案是：每 1000 个字符切一刀。

它会产生三个问题：

- 可能在一句话中间切开；
- 可能把代码块从中间切断；
- 中文、英文、代码的字符数与 Token 数关系不同。

例如：

```text
中文：线程池拒绝策略
英文：ThreadPoolExecutor rejection policy
代码：executor.execute(() -> doWork());
```

它们看起来字符数量接近，但经过模型 tokenizer 后，Token 数并不相同。

所以本阶段采用两层策略：

```text
第一层：按 Markdown 标题切，优先保证语义边界
第二层：章节仍然过长时，再按 Token 滑动窗口切
```

### 2.3 Token 是什么

模型不会直接读取“字符”，而是先通过 tokenizer 把文本编码成 Token ID。

可以把它暂时理解为：

```text
文本 --tokenizer--> 一串整数 --模型--> 向量
```

中文里一个汉字不一定永远等于一个 Token；英文单词也可能被拆成多个 Token。Token 数才更接近模型真正看到的输入大小。

本项目使用 Spring AI 已经带入的 JTokkit 做本地估算：

```java
EncodingType.CL100K_BASE
```

必须理解一个边界：`BAAI/bge-m3` 使用的真实 tokenizer 与 `CL100K_BASE` 并不完全相同。因此这里得到的是**稳定的本地预算值**，不是硅基流动服务端的绝对精确计费 Token 数。

这在智面的第一版是可以接受的，因为：

- 我们使用较保守的 `500` Token 目标值；
- `bge-m3` 的上下文上限远高于 500；
- 本阶段重点是稳定切片，而不是精确计算 API 账单；
- 后续可以用模型对应 tokenizer 替换当前实现，而不改上层 `MarkdownChunker`。

### 2.4 Overlap 是什么

假设一个长章节必须切成两段：

```text
chunk 0：A B C D E
chunk 1：F G H I J
```

如果关键结论横跨 `E` 和 `F`，两个 chunk 都可能缺少完整语义。Overlap 会让相邻片段重复一小段：

```text
chunk 0：A B C D E
chunk 1：D E F G H
```

这里的 `D E` 就是 overlap。

它的好处是减少边界信息丢失，代价是：

- 生成向量的文本总量增加；
- Redis 中的索引数据增加；
- 检索结果可能出现相邻片段同时命中；
- 后续需要做相邻 chunk 合并或去重。

本阶段推荐：

```yaml
chunk-size-tokens: 500
chunk-overlap-tokens: 80
```

80/500 约为 16%，适合作为第一版起点。不要在没有评估数据时反复凭感觉调参数。

### 2.5 为什么标题切片后仍要 Token 切片

Markdown 标题只能提供语义边界，不能保证长度。

下面这个章节即使只有一个标题，也可能有五千 Token：

```markdown
## ThreadPoolExecutor 源码分析

（很长的源码和逐行解释）
```

因此规则是：

```text
章节 <= 500 Token：原样成为一个 chunk
章节 > 500 Token：使用 500 Token 窗口切分，相邻窗口重叠 80 Token
```

### 2.6 切片阶段最重要的原则：不丢知识

不要为了让片段看起来整齐而删除“小片段”。一个只有十几个 Token 的结论也可能非常重要。

第一版遵循：

```text
空白片段丢弃
非空片段保留
短片段先不强行合并
```

后续在 07 阶段通过检索评估判断是否需要合并，而不是现在猜测。

---

## 三、本阶段的职责边界

### 3.1 本阶段负责

- 统一换行符；
- 清除文件开头的 BOM；
- 收敛多余空行；
- 保留代码块内部格式；
- 识别 Markdown 的 `#` 到 `######` 标题；
- 构建标题层级路径；
- 防止把代码块里的 `#` 当成标题；
- 使用 Token 窗口拆分超长章节；
- 为相邻长片段保留 overlap；
- 生成连续的 `chunkIndex`；
- 生成稳定的 `contentHash`；
- 编写不依赖外部服务的单元测试。

### 3.2 本阶段不负责

- 不接收 `MultipartFile`；
- 不设计上传 Controller；
- 不调用 `KnowledgeChunkMapper.batchInsert`；
- 不修改文档状态机；
- 不调用 Embedding API；
- 不写 Redis VectorStore；
- 不开异步线程；
- 不做失败重试；
- 不接入错题本和面试模块。

这些属于 06 及之后的阶段。

### 3.3 为什么要把切片器写成纯处理组件

如果 `MarkdownChunker` 一边切片、一边写 MySQL、一边调用 Embedding，会出现：

- 单元测试必须启动数据库和 Redis；
- 任何外部故障都会干扰切片算法测试；
- 无法快速比较不同切片参数；
- 方法失败时很难判断是解析错误、数据库错误还是模型错误。

正确分层是：

```text
MarkdownChunker
    只负责 文本 -> ChunkDraft 列表

KnowledgeIngestionService（06 阶段）
    负责编排 状态 -> 切片 -> MySQL -> Embedding -> Redis
```

这就是“算法与 IO 分离”。

---

## 四、最终文件结构

完成本阶段后新增或修改：

```text
src/main/java/com/zhimian
├── config
│   └── RagProperties.java                         修改
├── model/dto
│   └── KnowledgeChunkDraft.java                  新建
└── rag/support
    ├── KnowledgeTextCleaner.java                 新建
    ├── MarkdownSection.java                      新建
    ├── MarkdownSectionParser.java                新建
    ├── TokenWindowSplitter.java                  新建
    └── MarkdownChunker.java                      新建

src/test/java/com/zhimian/rag/support
├── KnowledgeTextCleanerTest.java                 新建
├── MarkdownSectionParserTest.java                新建
├── TokenWindowSplitterTest.java                  新建
└── MarkdownChunkerTest.java                      新建
```

每个类只承担一种职责：

| 类 | 职责 |
|---|---|
| `KnowledgeTextCleaner` | 对原始文本做保守、可预测的清洗 |
| `MarkdownSectionParser` | 根据标题生成语义章节和标题路径 |
| `TokenWindowSplitter` | 对超长文本执行 Token 滑动窗口切分 |
| `MarkdownChunker` | 编排前三个组件，生成最终 Draft |
| `KnowledgeChunkDraft` | 表达还没有写入数据库的 chunk |

---

## 五、第一步：配置切片参数

打开：

```text
src/main/resources/application.yml
```

在现有 `zhimian` 下增加公共的切片配置。项目当前的 Embedding API 地址、Key、模型与向量维度仍放在被 Git 忽略的 `application-local.yml`，而下面三个不敏感、需要团队共享的算法参数放在 `application.yml`：

```yaml
zhimian:
  rag:
    chunk-size-tokens: 500
    chunk-overlap-tokens: 80
    max-chunks-per-document: 1000
```

注意 YAML 层级。不要再写一个重复的 `zhimian:` 或 `rag:`。

然后修改：

```text
src/main/java/com/zhimian/config/RagProperties.java
```

增加三个字段：

```java
private int chunkSizeTokens = 500;
private int chunkOverlapTokens = 80;
private int maxChunksPerDocument = 1000;
```

完整结构应类似：

```java
package com.zhimian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zhimian.rag")
public class RagProperties {

    private boolean enabled = true;
    private int topK = 5;
    private double similarityThreshold = 0.70;
    private int embeddingDimensions = 1024;

    private int chunkSizeTokens = 500;
    private int chunkOverlapTokens = 80;
    private int maxChunksPerDocument = 1000;
}
```

### 5.1 三个参数分别控制什么

`chunkSizeTokens`：

```text
单个 Token 窗口最多包含多少 Token
```

`chunkOverlapTokens`：

```text
相邻 Token 窗口重复多少 Token
```

`maxChunksPerDocument`：

```text
一份文档最多允许产生多少 chunk
```

第三个参数是保护阈值。它可以防止错误文件或异常参数一次生成几十万个片段，把 MySQL、Embedding API 和 Redis 都拖入高负载。

必须满足：

```text
chunkSizeTokens > 0
chunkOverlapTokens >= 0
chunkOverlapTokens < chunkSizeTokens
maxChunksPerDocument > 0
```

最关键的是：

```text
overlap < chunkSize
```

否则窗口起点不会向后推进，会产生死循环。

---

## 六、第二步：定义切片结果 Draft

新建：

```text
src/main/java/com/zhimian/model/dto/KnowledgeChunkDraft.java
```

代码：

```java
package com.zhimian.model.dto;

public record KnowledgeChunkDraft(
        int chunkIndex,
        String headingPath,
        String content,
        String contentHash,
        int tokenCount
) {
}
```

### 6.1 为什么这里不用 `KnowledgeChunk`

现有 `KnowledgeChunk` 是数据库实体，包含：

```text
id
documentId
vectorStatus
createTime
updateTime
isDeleted
```

切片算法运行时，这些字段还不存在或不应该由算法决定。

如果直接返回 Entity，切片器就会开始知道数据库细节，职责会变脏。因此先返回 Draft：

```text
Draft：算法已经确定、但还未持久化的数据
Entity：数据库中的完整数据行
```

06 阶段会把 Draft 转成 Entity，并补上：

```text
documentId
vectorStatus = PENDING
```

### 6.2 为什么使用 `record`

Java 17 的 `record` 适合表达不可变的数据载体。编译器会自动生成：

- 构造器；
- 访问器，例如 `content()`；
- `equals`；
- `hashCode`；
- `toString`。

切片结果一旦创建就不应该被随意修改，所以它比带大量 setter 的普通 DTO 更合适。

注意 record 的读取方式：

```java
draft.content();
draft.headingPath();
draft.tokenCount();
```

不是：

```java
draft.getContent();
```

---

## 七、第三步：定义 Markdown 章节对象

新建：

```text
src/main/java/com/zhimian/rag/support/MarkdownSection.java
```

代码：

```java
package com.zhimian.rag.support;

public record MarkdownSection(
        String headingPath,
        String content
) {
}
```

它表示“按标题切完、尚未按 Token 二次切分”的中间结果。

例如：

```markdown
# Java
## 并发
### volatile
volatile 保证可见性和有序性，但不保证复合操作的原子性。
```

会生成：

```text
headingPath = Java > 并发 > volatile
content = volatile 保证可见性和有序性，但不保证复合操作的原子性。
```

这是内部中间模型，不需要进数据库。

---

## 八、第四步：实现保守文本清洗

新建：

```text
src/main/java/com/zhimian/rag/support/KnowledgeTextCleaner.java
```

代码：

```java
package com.zhimian.rag.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeTextCleaner {

    private static final char BOM = '\uFEFF';
    private static final int MAX_CONSECUTIVE_BLANK_LINES = 2;

    public String clean(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }

        String normalized = content
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        if (!normalized.isEmpty() && normalized.charAt(0) == BOM) {
            normalized = normalized.substring(1);
        }

        String[] lines = normalized.split("\n", -1);
        StringBuilder result = new StringBuilder(normalized.length());

        boolean inCodeFence = false;
        String fenceMarker = null;
        int consecutiveBlankLines = 0;

        for (String line : lines) {
            String leftTrimmed = line.stripLeading();

            if (inCodeFence) {
                appendLine(result, line);
                if (isClosingFence(leftTrimmed, fenceMarker)) {
                    inCodeFence = false;
                    fenceMarker = null;
                }
                continue;
            }

            String openingFence = findFenceMarker(leftTrimmed);
            if (openingFence != null) {
                appendLine(result, line.stripTrailing());
                inCodeFence = true;
                fenceMarker = openingFence;
                consecutiveBlankLines = 0;
                continue;
            }

            String cleanedLine = line.stripTrailing();
            if (cleanedLine.isBlank()) {
                consecutiveBlankLines++;
                if (consecutiveBlankLines > MAX_CONSECUTIVE_BLANK_LINES) {
                    continue;
                }
            }
            else {
                consecutiveBlankLines = 0;
            }

            appendLine(result, cleanedLine);
        }

        return result.toString().strip();
    }

    private String findFenceMarker(String line) {
        if (line.isEmpty()
                || (line.charAt(0) != '`' && line.charAt(0) != '~')) {
            return null;
        }

        char marker = line.charAt(0);
        int length = 0;
        while (length < line.length()
                && line.charAt(length) == marker) {
            length++;
        }
        return length >= 3 ? line.substring(0, length) : null;
    }

    private boolean isClosingFence(String line, String openingFence) {
        if (line.isEmpty() || line.charAt(0) != openingFence.charAt(0)) {
            return false;
        }

        char marker = line.charAt(0);
        int length = 0;
        while (length < line.length() && line.charAt(length) == marker) {
            length++;
        }
        return length >= openingFence.length()
                && line.substring(length).isBlank();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(line);
    }
}
```

### 8.1 清洗器做了什么

第一步统一换行符：

```java
.replace("\r\n", "\n")
.replace('\r', '\n');
```

Windows 通常是 `\r\n`，Linux 通常是 `\n`。统一后，相同文档不会因为操作系统不同产生不同 Hash 和切片结果。

第二步删除开头 BOM：

```java
if (!normalized.isEmpty() && normalized.charAt(0) == BOM) {
    normalized = normalized.substring(1);
}
```

BOM 是某些 UTF-8 文件开头的隐藏字符。人眼看不到，但它会影响标题识别与 Hash。

第三步只在代码块外收敛多余空行：

```text
连续 1 或 2 个空行：保留
连续第 3 个及以后：丢弃
```

第四步保留代码块内部内容：

```java
if (inCodeFence) {
    appendLine(result, line);
}
```

代码的缩进、尾部空格有时有语义，清洗器不能像普通文章一样粗暴处理。

### 8.2 为什么不删除所有 HTML、标点和空格

清洗不是“删得越多越好”。下面这些信息可能影响语义：

- `!=` 与 `==`；
- Java 缩进和代码行；
- Markdown 表格；
- 列表顺序；
- 反引号里的类名；
- 中英文标点。

第一版只处理确定无害的格式噪音。过度清洗会让知识不可逆地丢失。

---

## 九、第五步：按 Markdown 标题解析章节

新建：

```text
src/main/java/com/zhimian/rag/support/MarkdownSectionParser.java
```

代码：

```java
package com.zhimian.rag.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownSectionParser {

    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*$");

    public List<MarkdownSection> parse(String markdown,
                                       String documentTitle) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }

        String fallbackPath = StringUtils.hasText(documentTitle)
                ? documentTitle.strip()
                : "正文";

        List<MarkdownSection> sections = new ArrayList<>();
        String[] headingLevels = new String[6];
        String currentPath = fallbackPath;
        StringBuilder currentContent = new StringBuilder();

        boolean inCodeFence = false;
        String fenceMarker = null;

        for (String line : markdown.split("\n", -1)) {
            String leftTrimmed = line.stripLeading();

            if (inCodeFence) {
                appendLine(currentContent, line);
                if (isClosingFence(leftTrimmed, fenceMarker)) {
                    inCodeFence = false;
                    fenceMarker = null;
                }
                continue;
            }

            String openingFence = findFenceMarker(leftTrimmed);
            if (openingFence != null) {
                inCodeFence = true;
                fenceMarker = openingFence;
                appendLine(currentContent, line);
                continue;
            }

            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                addSectionIfPresent(sections, currentPath, currentContent);

                int level = matcher.group(1).length();
                String heading = matcher.group(2)
                        .replaceFirst("\\s+#+\\s*$", "")
                        .strip();
                headingLevels[level - 1] = heading;
                Arrays.fill(headingLevels, level, headingLevels.length, null);

                currentPath = buildHeadingPath(headingLevels, fallbackPath);
                currentContent = new StringBuilder();
                continue;
            }

            appendLine(currentContent, line);
        }

        addSectionIfPresent(sections, currentPath, currentContent);
        return List.copyOf(sections);
    }

    private void addSectionIfPresent(List<MarkdownSection> sections,
                                     String headingPath,
                                     StringBuilder content) {
        String value = content.toString().strip();
        if (StringUtils.hasText(value)) {
            sections.add(new MarkdownSection(headingPath, value));
        }
    }

    private String buildHeadingPath(String[] levels, String fallbackPath) {
        List<String> pathParts = Arrays.stream(levels)
                .filter(StringUtils::hasText)
                .toList();
        return pathParts.isEmpty()
                ? fallbackPath
                : String.join(" > ", pathParts);
    }

    private String findFenceMarker(String line) {
        if (line.isEmpty()
                || (line.charAt(0) != '`' && line.charAt(0) != '~')) {
            return null;
        }

        char marker = line.charAt(0);
        int length = 0;
        while (length < line.length()
                && line.charAt(length) == marker) {
            length++;
        }
        return length >= 3 ? line.substring(0, length) : null;
    }

    private boolean isClosingFence(String line, String openingFence) {
        if (line.isEmpty() || line.charAt(0) != openingFence.charAt(0)) {
            return false;
        }

        char marker = line.charAt(0);
        int length = 0;
        while (length < line.length() && line.charAt(length) == marker) {
            length++;
        }
        return length >= openingFence.length()
                && line.substring(length).isBlank();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(line);
    }
}
```

### 9.1 标题正则如何理解

```java
Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*$");
```

拆开看：

| 部分 | 含义 |
|---|---|
| `^\\s{0,3}` | 一行开始，允许 Markdown 标准中的最多三个前导空格 |
| `(#{1,6})` | 1 到 6 个 `#`，并捕获标题等级 |
| `\\s+` | `#` 后至少有一个空白字符 |
| `(.+?)` | 捕获标题正文 |
| `$` | 一行结束 |

因此：

```text
## 线程池          能识别
## 线程池 ##       能识别
##线程池           不识别，因为不是标准 ATX 标题
C#                 标题正文中的井号会保留
```

标题末尾可选的关闭井号在捕获后单独清理：

```java
.replaceFirst("\\s+#+\\s*$", "")
```

这里要求井号前存在空白，因此 `C#` 不会被错误清洗成 `C`。

### 9.2 标题栈为什么用长度为 6 的数组

Markdown 标题只有六级。假设依次遇到：

```markdown
# Java
## 并发
### AQS
## JVM
```

数组变化：

```text
遇到 # Java： [Java, null, null, null, null, null]
遇到 ## 并发：[Java, 并发, null, null, null, null]
遇到 ### AQS：[Java, 并发, AQS, null, null, null]
遇到 ## JVM： [Java, JVM, null, null, null, null]
```

遇到二级标题 `JVM` 时，三级及更深标题必须清空：

```java
Arrays.fill(headingLevels, level, headingLevels.length, null);
```

否则会错误得到：

```text
Java > JVM > AQS
```

### 9.3 为什么标题行不直接放入正文

标题已经结构化保存在 `headingPath` 中。06 阶段构造 Embedding 文本时，可以明确组合：

```text
知识路径：Java > 并发 > AQS

AQS 是构建锁和同步器的基础框架……
```

这样比把 `#`、`##` 原样混在正文里更容易控制。

### 9.4 为什么代码块里也要跟踪 Fence

Markdown 代码块里可能出现：

````markdown
```java
# 这不是 Markdown 标题
```
````

如果不记录 `inCodeFence`，解析器会把代码中的 `#` 当成标题，导致代码块被切碎。

这里实现的是智面当前所需的“小型状态机”，不是完整 CommonMark 解析器。状态只有：

```text
代码块外
代码块内
```

当未来需要完整支持 Setext 标题、HTML Block、复杂嵌套引用时，再引入 CommonMark Parser。当前只支持 Markdown/TXT 技术资料，这个边界足够明确，也更适合你理解切片核心过程。

---

## 十、第六步：实现 Token 滑动窗口

Spring AI 1.0.0 已经间接使用 JTokkit。由于我们的代码会直接 import JTokkit 类型，最好把它声明为项目的直接依赖，而不是偷偷依赖别的包传递进来。

在 `pom.xml` 的 properties 中增加：

```xml
<jtokkit.version>1.1.0</jtokkit.version>
```

在 dependencies 中增加：

```xml
<dependency>
    <groupId>com.knuddels</groupId>
    <artifactId>jtokkit</artifactId>
    <version>${jtokkit.version}</version>
</dependency>
```

然后新建：

```text
src/main/java/com/zhimian/rag/support/TokenWindowSplitter.java
```

代码：

```java
package com.zhimian.rag.support;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class TokenWindowSplitter {

    private final Encoding encoding = Encodings
            .newLazyEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    public int countTokens(String text) {
        if (text == null) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    public List<String> split(String text,
                              int maxTokens,
                              int overlapTokens) {
        validateWindow(maxTokens, overlapTokens);

        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        String normalized = text.strip();
        IntArrayList tokenIds = encoding.encode(normalized);

        if (tokenIds.size() <= maxTokens) {
            return List.of(normalized);
        }

        boolean[] safeBoundaries = findSafeBoundaries(tokenIds);
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < tokenIds.size()) {
            int proposedEnd = Math.min(start + maxTokens, tokenIds.size());
            int end = findSafeEnd(safeBoundaries, start, proposedEnd);
            IntArrayList window = copyRange(tokenIds, start, end);
            String chunk = encoding.decode(window).strip();

            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }

            if (end == tokenIds.size()) {
                break;
            }

            int nextStart = end - overlapTokens;
            while (nextStart < end && !safeBoundaries[nextStart]) {
                nextStart++;
            }
            start = nextStart > start ? nextStart : end;
        }

        return List.copyOf(chunks);
    }

    private boolean[] findSafeBoundaries(IntArrayList tokenIds) {
        boolean[] safeBoundaries = new boolean[tokenIds.size() + 1];
        safeBoundaries[0] = true;
        safeBoundaries[tokenIds.size()] = true;

        for (int index = 1; index < tokenIds.size(); index++) {
            IntArrayList token = new IntArrayList(1);
            token.add(tokenIds.get(index));
            byte[] bytes = encoding.decodeBytes(token);
            safeBoundaries[index] = bytes.length == 0
                    || !isUtf8ContinuationByte(bytes[0]);
        }
        return safeBoundaries;
    }

    private int findSafeEnd(boolean[] safeBoundaries,
                            int start,
                            int proposedEnd) {
        int end = proposedEnd;
        while (end > start && !safeBoundaries[end]) {
            end--;
        }
        if (end == start) {
            throw new IllegalArgumentException(
                    "maxTokens 过小，无法容纳一个完整字符");
        }
        return end;
    }

    private boolean isUtf8ContinuationByte(byte value) {
        return (value & 0xC0) == 0x80;
    }

    private IntArrayList copyRange(IntArrayList source,
                                   int start,
                                   int end) {
        IntArrayList result = new IntArrayList(end - start);
        for (int index = start; index < end; index++) {
            result.add(source.get(index));
        }
        return result;
    }

    private void validateWindow(int maxTokens, int overlapTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException(
                    "maxTokens 必须大于 0");
        }
        if (overlapTokens < 0 || overlapTokens >= maxTokens) {
            throw new IllegalArgumentException(
                    "overlapTokens 必须大于等于 0 且小于 maxTokens");
        }
    }
}
```

### 10.1 overlap 的基本步长为什么是减法

```java
int conceptualStep = maxTokens - overlapTokens;
```

如果：

```text
maxTokens = 500
overlapTokens = 80
```

那么：

```text
第 1 段：Token [0, 500)
第 2 段：Token [420, 920)
第 3 段：Token [840, 1340)
```

第二段从 420 开始，因此与第一段重复 `[420, 500)`，目标是 80 Token。实际代码还要把起止位置调整到安全的 UTF-8 字符边界，因此中文场景的真实 overlap 可能比配置少一两个 Token，但不会产生乱码。

### 10.2 为什么使用左闭右开区间

Java 中常用：

```text
[start, end)
```

表示包含 `start`，不包含 `end`。

优点是片段长度直接为：

```text
end - start
```

这与 `substring`、`subList` 的习惯一致，能减少加一减一错误。

### 10.3 为什么 Token 边界不一定是字符边界

```java
IntArrayList tokenIds = encoding.encode(normalized);
```

先编码后可以控制 Token 数，但 JTokkit 使用字节级 BPE，某些中文字符的 UTF-8 字节可能横跨两个 Token。如果直接在任意 Token 位置切开并调用：

```java
encoding.decode(window)
```

还原文本，窗口边缘可能出现 Unicode 替换字符：

```text
�
```

因此代码先计算 `safeBoundaries`。UTF-8 延续字节的二进制前缀是 `10xxxxxx`，判断方式是：

```java
(value & 0xC0) == 0x80
```

如果某个 Token 的第一个字节是延续字节，说明这个位置处于一个字符内部，不能作为窗口边界。

窗口结束位置从 `proposedEnd` 向前收缩到最近安全边界：

```java
int end = findSafeEnd(safeBoundaries, start, proposedEnd);
```

下一个窗口的起点则向后移动到安全边界：

```java
while (nextStart < end && !safeBoundaries[nextStart]) {
    nextStart++;
}
```

这样同时保证：

- 单个窗口不超过 `maxTokens`；
- 中文和 Emoji 不产生 `�`；
- 文本不会因为窗口边界丢失半个字符；
- 窗口始终向前推进。

### 10.4 为什么最后必须主动 break

```java
if (end == tokenIds.size()) {
    break;
}
```

最后一个窗口已经覆盖所有剩余 Token 后立刻退出，避免因为 overlap 再生成一个只包含末尾重复内容的冗余片段。

---

## 十一、第七步：实现总编排器 MarkdownChunker

新建：

```text
src/main/java/com/zhimian/rag/support/MarkdownChunker.java
```

代码：

```java
package com.zhimian.rag.support;

import com.zhimian.config.RagProperties;
import com.zhimian.model.dto.KnowledgeChunkDraft;
import com.zhimian.util.KnowledgeContentHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MarkdownChunker {

    private static final String MARKDOWN = "MARKDOWN";
    private static final String TEXT = "TEXT";

    private final KnowledgeTextCleaner cleaner;
    private final MarkdownSectionParser sectionParser;
    private final TokenWindowSplitter tokenSplitter;
    private final KnowledgeContentHasher contentHasher;
    private final RagProperties ragProperties;

    public List<KnowledgeChunkDraft> chunk(String documentTitle,
                                            String sourceType,
                                            String content) {
        validateInput(documentTitle, sourceType, content);
        validateConfiguration();

        String cleaned = cleaner.clean(content);
        if (!StringUtils.hasText(cleaned)) {
            return List.of();
        }

        String normalizedType = sourceType.strip()
                .toUpperCase(Locale.ROOT);
        List<MarkdownSection> sections = buildSections(
                documentTitle.strip(), normalizedType, cleaned);

        List<KnowledgeChunkDraft> drafts = new ArrayList<>();
        int chunkIndex = 0;

        for (MarkdownSection section : sections) {
            List<String> windows = tokenSplitter.split(
                    section.content(),
                    ragProperties.getChunkSizeTokens(),
                    ragProperties.getChunkOverlapTokens()
            );

            for (String window : windows) {
                if (drafts.size()
                        >= ragProperties.getMaxChunksPerDocument()) {
                    throw new IllegalArgumentException(
                            "文档切片数量超过系统上限");
                }

                String identityText = section.headingPath()
                        + "\n\n"
                        + window;

                drafts.add(new KnowledgeChunkDraft(
                        chunkIndex++,
                        section.headingPath(),
                        window,
                        contentHasher.sha256(identityText),
                        tokenSplitter.countTokens(window)
                ));
            }
        }

        return List.copyOf(drafts);
    }

    private List<MarkdownSection> buildSections(String documentTitle,
                                                String sourceType,
                                                String cleaned) {
        if (MARKDOWN.equals(sourceType)) {
            return sectionParser.parse(cleaned, documentTitle);
        }
        if (TEXT.equals(sourceType)) {
            return List.of(new MarkdownSection(
                    documentTitle, cleaned));
        }
        throw new IllegalArgumentException(
                "当前只支持 MARKDOWN 和 TEXT 文档");
    }

    private void validateInput(String documentTitle,
                               String sourceType,
                               String content) {
        if (!StringUtils.hasText(documentTitle)) {
            throw new IllegalArgumentException("文档标题不能为空");
        }
        if (!StringUtils.hasText(sourceType)) {
            throw new IllegalArgumentException("文档类型不能为空");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
    }

    private void validateConfiguration() {
        int chunkSize = ragProperties.getChunkSizeTokens();
        int overlap = ragProperties.getChunkOverlapTokens();
        int maxChunks = ragProperties.getMaxChunksPerDocument();

        if (chunkSize <= 0) {
            throw new IllegalStateException(
                    "RAG chunkSizeTokens 配置必须大于 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalStateException(
                    "RAG chunkOverlapTokens 必须大于等于 0 且小于 chunkSizeTokens");
        }
        if (maxChunks <= 0) {
            throw new IllegalStateException(
                    "RAG maxChunksPerDocument 配置必须大于 0");
        }
    }
}
```

### 11.1 总流程逐行理解

第一步验证业务输入：

```java
validateInput(documentTitle, sourceType, content);
```

这里验证调用者有没有传值。

第二步验证系统配置：

```java
validateConfiguration();
```

这里验证开发者是否把 YAML 配错。两类错误的责任不同，所以异常类型也分开：

```text
IllegalArgumentException：调用参数不合法
IllegalStateException：系统配置或运行状态不合法
```

第三步清洗：

```java
String cleaned = cleaner.clean(content);
```

第四步按文档类型创建章节：

```text
MARKDOWN：解析标题结构
TEXT：整篇先视为一个章节，路径就是文档标题
```

第五步逐章节做 Token 窗口切分：

```java
tokenSplitter.split(section.content(), size, overlap)
```

短章节只返回一段，长章节返回多段。

第六步创建 Draft：

```java
new KnowledgeChunkDraft(...)
```

此时不设置 `documentId` 和 `vectorStatus`，因为它们属于 06 阶段的持久化编排。

### 11.2 为什么 Hash 同时包含标题路径和正文

代码使用：

```java
String identityText = section.headingPath()
        + "\n\n"
        + window;
```

假设不同章节都出现一句：

```text
默认值为 10。
```

仅对正文 Hash，两段会得到相同 Hash。但它们可能分别属于：

```text
线程池 > corePoolSize
数据库 > maximumPoolSize
```

语义位置不同，不应该在同一文档内被误判为相同片段。把 `headingPath` 纳入片段身份可以解决这个问题。

注意数据库里仍然分别保存：

```text
heading_path = 标题路径
content = 原始片段正文
content_hash = 标题路径 + 正文 的 SHA-256
```

### 11.3 为什么返回 `List.copyOf`

```java
return List.copyOf(drafts);
```

它返回不可修改列表。调用方不能意外执行：

```java
drafts.clear();
```

这能让切片结果在进入 06 阶段后保持稳定。

### 11.4 为什么 `chunkIndex` 从 0 开始

数组、List 和 Token 窗口都使用 0 基索引，因此：

```text
第一个 chunkIndex = 0
第二个 chunkIndex = 1
```

后续相邻片段定位可以直接使用：

```text
previous = index - 1
next = index + 1
```

关键不是必须从 0 开始，而是整个项目必须统一。

---

## 十二、这套算法面对一篇文档时发生了什么

输入：

````markdown
# Java 并发

并发编程需要关注可见性、原子性和有序性。

## volatile

volatile 能保证可见性和有序性，但不能保证复合操作的原子性。

```java
// 代码块里的 # 不是标题
volatile boolean running = true;
```

## 线程池

（假设这里有 1200 Token 的长内容）
````

处理过程：

```text
1. Cleaner 统一换行、删除 BOM、收敛空行
2. Parser 创建三个 section
3. Java 并发正文较短 -> 1 个 chunk
4. volatile 正文较短 -> 1 个 chunk
5. 线程池正文 1200 Token -> 多个带 overlap 的 chunk
6. 所有 chunk 获得全局连续 index
7. 每个 chunk 计算 tokenCount 与 contentHash
```

可能输出：

```text
chunk 0
path: Java 并发
tokens: 22

chunk 1
path: Java 并发 > volatile
tokens: 60

chunk 2
path: Java 并发 > 线程池
tokens: 500

chunk 3
path: Java 并发 > 线程池
tokens: 500

chunk 4
path: Java 并发 > 线程池
tokens: 360
```

注意最后三个片段 Token 数相加会大于 1200，因为 overlap 被重复计算了。这是预期行为。

---

## 十三、第八步：先测试 Cleaner

新建：

```text
src/test/java/com/zhimian/rag/support/KnowledgeTextCleanerTest.java
```

代码：

```java
package com.zhimian.rag.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeTextCleanerTest {

    private final KnowledgeTextCleaner cleaner =
            new KnowledgeTextCleaner();

    @Test
    void shouldNormalizeLineEndingsAndRemoveBom() {
        String input = "\uFEFF# Java\r\n\r正文\n";

        String result = cleaner.clean(input);

        assertEquals("# Java\n\n正文", result);
    }

    @Test
    void shouldCollapseExcessBlankLinesOutsideCodeFence() {
        String input = "第一段\n\n\n\n第二段";

        String result = cleaner.clean(input);

        assertEquals("第一段\n\n\n第二段", result);
    }

    @Test
    void shouldPreserveCodeFenceContent() {
        String input = "```text\nline 1  \n\n\n\nline 2\n```";

        String result = cleaner.clean(input);

        assertEquals(input, result);
    }
}
```

### 13.1 为什么这是单元测试

它只创建一个 Java 对象：

```java
new KnowledgeTextCleaner()
```

不启动 Spring，不连接 MySQL，不启动 Redis，也不访问网络。因此执行速度快、失败原因明确。

### 13.2 为什么代码块测试很重要

如果只测试普通段落，即使清洗器错误地删除代码缩进，测试也发现不了。测试应覆盖“最容易被算法破坏的内容”，而不只是覆盖最简单的成功路径。

---

## 十四、第九步：测试标题解析器

新建：

```text
src/test/java/com/zhimian/rag/support/MarkdownSectionParserTest.java
```

代码：

```java
package com.zhimian.rag.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownSectionParserTest {

    private final MarkdownSectionParser parser =
            new MarkdownSectionParser();

    @Test
    void shouldBuildNestedHeadingPath() {
        String markdown = """
                # Java
                Java 基础正文。
                ## 并发
                并发正文。
                ### AQS
                AQS 正文。
                ## JVM
                JVM 正文。
                """;

        List<MarkdownSection> sections =
                parser.parse(markdown, "Java 指南");

        assertEquals(4, sections.size());
        assertEquals("Java", sections.get(0).headingPath());
        assertEquals("Java > 并发", sections.get(1).headingPath());
        assertEquals("Java > 并发 > AQS",
                sections.get(2).headingPath());
        assertEquals("Java > JVM", sections.get(3).headingPath());
    }

    @Test
    void shouldUseDocumentTitleBeforeFirstHeading() {
        String markdown = "开场说明。\n\n# Java\nJava 正文。";

        List<MarkdownSection> sections =
                parser.parse(markdown, "后端手册");

        assertEquals("后端手册", sections.get(0).headingPath());
        assertEquals("开场说明。", sections.get(0).content());
    }

    @Test
    void shouldNotTreatHashInsideCodeFenceAsHeading() {
        String markdown = """
                # Shell
                ```bash
                # 这是一行 shell 注释
                echo hello
                ```
                后续正文。
                """;

        List<MarkdownSection> sections =
                parser.parse(markdown, "命令手册");

        assertEquals(1, sections.size());
        assertEquals("Shell", sections.get(0).headingPath());
        assertTrue(sections.get(0).content()
                .contains("# 这是一行 shell 注释"));
    }

    @Test
    void shouldPreserveHashInHeadingText() {
        List<MarkdownSection> sections = parser.parse(
                "# C#\nC# 基础正文。", "语言手册");

        assertEquals("C#", sections.get(0).headingPath());
    }
}
```

### 14.1 这三个测试分别保护什么

| 测试 | 防止的回归 |
|---|---|
| 嵌套路径 | 返回上级标题时没有清理旧的下级标题 |
| 标题前正文 | 文档摘要、前言被静默丢失 |
| 代码块中的 `#` | 代码被误切成多个章节 |
| 标题正文中的 `#` | `C#` 被错误清洗成 `C` |

---

## 十五、第十步：测试 Token 窗口

新建：

```text
src/test/java/com/zhimian/rag/support/TokenWindowSplitterTest.java
```

代码：

```java
package com.zhimian.rag.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenWindowSplitterTest {

    private final TokenWindowSplitter splitter =
            new TokenWindowSplitter();

    @Test
    void shouldKeepShortTextInOneChunk() {
        List<String> chunks = splitter.split(
                "volatile 保证可见性", 100, 20);

        assertEquals(1, chunks.size());
        assertEquals("volatile 保证可见性", chunks.get(0));
    }

    @Test
    void shouldSplitLongTextWithinTokenLimit() {
        String content = "线程池用于复用工作线程。".repeat(200);

        List<String> chunks = splitter.split(content, 80, 10);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream()
                .allMatch(chunk -> splitter.countTokens(chunk) <= 80));
        assertFalse(chunks.stream()
                .anyMatch(chunk -> chunk.contains("\uFFFD")));
    }

    @Test
    void shouldRejectInvalidOverlap() {
        assertThrows(IllegalArgumentException.class,
                () -> splitter.split("正文", 100, 100));
    }
}
```

这里不应该断言“中文一句话一定是多少 Token”，因为 tokenizer 版本改变时具体数字可能变化。测试真正稳定的业务性质：

```text
短文本不乱切
长文本能够切开
每个窗口不超过上限
中文窗口边界不产生 Unicode 替换字符 `�`
非法配置被拒绝
```

这叫测试行为，而不是把第三方实现细节写死。

---

## 十六、第十一步：测试完整切片器

新建：

```text
src/test/java/com/zhimian/rag/support/MarkdownChunkerTest.java
```

代码：

```java
package com.zhimian.rag.support;

import com.zhimian.config.RagProperties;
import com.zhimian.model.dto.KnowledgeChunkDraft;
import com.zhimian.util.KnowledgeContentHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownChunkerTest {

    private MarkdownChunker chunker;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setChunkSizeTokens(80);
        properties.setChunkOverlapTokens(10);
        properties.setMaxChunksPerDocument(100);

        chunker = new MarkdownChunker(
                new KnowledgeTextCleaner(),
                new MarkdownSectionParser(),
                new TokenWindowSplitter(),
                new KnowledgeContentHasher(),
                properties
        );
    }

    @Test
    void shouldChunkMarkdownByHeading() {
        String markdown = """
                # Java
                Java 是面向对象语言。
                ## JVM
                JVM 负责执行字节码。
                """;

        List<KnowledgeChunkDraft> drafts = chunker.chunk(
                "Java 手册", "markdown", markdown);

        assertEquals(2, drafts.size());
        assertEquals(0, drafts.get(0).chunkIndex());
        assertEquals(1, drafts.get(1).chunkIndex());
        assertEquals("Java", drafts.get(0).headingPath());
        assertEquals("Java > JVM", drafts.get(1).headingPath());
    }

    @Test
    void shouldTreatTextAsSingleSectionBeforeTokenSplit() {
        List<KnowledgeChunkDraft> drafts = chunker.chunk(
                "数据库说明", "TEXT", "MySQL 使用 B+Tree 索引。");

        assertEquals(1, drafts.size());
        assertEquals("数据库说明", drafts.get(0).headingPath());
    }

    @Test
    void shouldProduceStableHashForSameInput() {
        String markdown = "# Redis\nRedis 是内存数据结构存储。";

        List<KnowledgeChunkDraft> first = chunker.chunk(
                "Redis 手册", "MARKDOWN", markdown);
        List<KnowledgeChunkDraft> second = chunker.chunk(
                "Redis 手册", "MARKDOWN", markdown);

        assertEquals(first, second);
        assertFalse(first.get(0).contentHash().isBlank());
        assertEquals(64, first.get(0).contentHash().length());
    }

    @Test
    void shouldSplitOversizedSectionAndKeepTokenLimit() {
        String markdown = "# 线程池\n"
                + "线程池用于复用工作线程。".repeat(200);

        List<KnowledgeChunkDraft> drafts = chunker.chunk(
                "并发手册", "MARKDOWN", markdown);

        assertTrue(drafts.size() > 1);
        assertTrue(drafts.stream()
                .allMatch(draft -> draft.tokenCount() <= 80));
    }

    @Test
    void shouldRejectUnsupportedSourceType() {
        assertThrows(IllegalArgumentException.class,
                () -> chunker.chunk(
                        "PDF 文档", "PDF", "正文"));
    }
}
```

### 16.1 为什么测试里不用 `@SpringBootTest`

这些对象都可以直接 new，不需要 Spring 容器：

```java
new MarkdownChunker(...)
```

使用 `@SpringBootTest` 反而会：

- 启动完整应用；
- 尝试加载数据库、Redis、AI 配置；
- 让测试变慢；
- 让切片错误被外部配置错误掩盖。

这也是依赖注入的重要价值：生产环境由 Spring 注入，单元测试由测试代码手动注入。

### 16.2 为什么要测试结果稳定性

如果相同文档每次导入产生不同切片或 Hash，会导致：

- 重复数据无法识别；
- Redis 出现旧向量；
- 文档重建后检索结果无规律变化；
- 线上问题难以复现。

所以：

```java
assertEquals(first, second);
```

不是为了凑覆盖率，而是在保护 RAG 数据管道的幂等基础。

---

## 十七、运行测试

先只运行 05 阶段的测试：

```powershell
cd E:\java-code\ZhiMian

mvn "-Dtest=KnowledgeTextCleanerTest,MarkdownSectionParserTest,TokenWindowSplitterTest,MarkdownChunkerTest" test
```

Windows PowerShell 中建议把包含逗号的 `-Dtest=...` 整体放进双引号，避免命令解析差异。

然后运行完整后端测试：

```powershell
mvn test
```

最后检查格式：

```powershell
git diff --check
git status --short
```

本阶段测试完全不需要：

- Docker Desktop；
- MySQL；
- Redis Stack；
- Embedding API Key；
- 网络连接。

如果这些测试要求外部环境，说明你的切片器与 IO 层耦合了，需要重新拆分。

---

## 十八、常见错误与排查方法

### 18.1 `getContent()` 编译失败

如果对象是 record：

```java
KnowledgeChunkDraft draft
```

读取方式是：

```java
draft.content()
```

不是：

```java
draft.getContent()
```

### 18.2 找不到 JTokkit 类

报错类似：

```text
package com.knuddels.jtokkit does not exist
```

检查 `pom.xml` 是否显式增加了：

```xml
<dependency>
    <groupId>com.knuddels</groupId>
    <artifactId>jtokkit</artifactId>
    <version>1.1.0</version>
</dependency>
```

然后执行：

```powershell
mvn dependency:tree
mvn clean test
```

### 18.3 无限循环或内存快速增长

优先检查：

```text
chunkOverlapTokens >= chunkSizeTokens
```

因为：

```text
step = chunkSize - overlap
```

当 `step <= 0` 时，窗口不会前进。

### 18.4 代码块中的注释被识别成标题

检查解析器是否：

- 先判断当前是否在代码块；
- 再判断 Markdown 标题；
- 开始和结束 Fence 是否使用同一种标记。

不要只用一条全局正则把整篇 Markdown 切开。正则本身不知道匹配发生在正文还是代码块中。

### 18.5 标题路径出现旧的下级标题

例如错误结果：

```text
Java > JVM > AQS
```

检查遇到新标题时是否执行：

```java
Arrays.fill(headingLevels, level, headingLevels.length, null);
```

### 18.6 同一份文档每次 Hash 不同

检查：

- 换行是否统一；
- BOM 是否删除；
- 清洗顺序是否固定；
- Hash 前是否拼入了随机值、时间或数据库 ID；
- 是否使用固定的 UTF-8 编码。

Hash 输入只能由稳定业务内容组成。

### 18.7 最后出现一个只有重复内容的 chunk

检查窗口循环是否在覆盖最后 Token 后执行：

```java
if (end == tokenIds.size()) {
    break;
}
```

### 18.8 一个章节只有标题，没有正文时为什么不生成 chunk

标题用于组织正文，本身通常不构成可回答知识。当前解析器只在正文非空时创建 section。

如果未来知识库里存在大量“标题本身就是结论”的文档，应通过测试和评估再修改规则，不要为假设场景提前增加复杂度。

---

## 十九、为什么暂时不直接用 Spring AI 的 TokenTextSplitter

Spring AI 1.0.0 提供：

```java
TokenTextSplitter
```

它非常适合普通长文本的 Token 切分，但智面当前还有两个额外需求：

1. 必须保留 Markdown 标题层级路径；
2. 必须明确控制 overlap。

因此本阶段不是否定 `TokenTextSplitter`，而是把问题拆开：

```text
MarkdownSectionParser：负责文档结构
TokenWindowSplitter：负责长度和 overlap
MarkdownChunker：负责组合
```

以后可以把 `TokenWindowSplitter` 的内部实现替换为其他 splitter，上层 API 不需要改变。这是“面向接口边界设计”的实际例子。

---

## 二十、第一版算法的已知限制

你需要知道自己写的代码能做什么，也要知道它不能做什么。

当前版本支持：

- UTF-8 Markdown/TXT；
- ATX 标题，即 `# 标题`；
- 三反引号代码块；
- 三波浪线代码块；
- 标题层级路径；
- Token 上限；
- 固定 Token overlap。

当前版本没有专门支持：

- `标题\n====` 形式的 Setext 标题；
- PDF 和 Word 解析；
- HTML DOM 清洗；
- Markdown 表格的独立切分策略；
- 超长代码块的语法级切分；
- 按自然段或句号吸附窗口边界；
- `bge-m3` 官方 tokenizer 的精确 Token 计数；
- 按内容类型动态设置 chunk size；
- 中文分词和关键词抽取。

这些不是当前 bug，而是第一版明确的能力边界。是否优化要由 07 阶段评估结果决定。

---

## 二十一、切片质量如何影响后续检索

### 21.1 Chunk 太大

可能出现：

- 一个向量混入多个主题；
- 相似度不够集中；
- 返回给 ChatModel 的无关文本增多；
- Prompt 变长，生成成本和延迟增加。

### 21.2 Chunk 太小

可能出现：

- 定义与原因被拆开；
- 命中片段缺少上下文；
- chunk 数量和 Embedding 调用量增加；
- 相邻片段大量重复命中。

### 21.3 Overlap 太大

可能出现：

- 存储和调用成本上升；
- TopK 被同一章节的相邻片段占满；
- 返回给模型的内容重复。

### 21.4 Overlap 太小

可能出现：

- 边界处信息断裂；
- 问题需要的原因和结论分别落在两个 chunk；
- 单个命中片段无法独立回答。

所以参数不存在永远正确的答案。正确方法是：

```text
先用 500/80 建立基线
    ↓
07 阶段建立真实问题评估集
    ↓
比较 Hit@K、相关性和重复率
    ↓
有证据地调整参数
```

---

## 二十二、与 04 数据模型如何衔接

本阶段输出：

```java
KnowledgeChunkDraft
```

06 阶段会转换为：

```java
KnowledgeChunk entity = new KnowledgeChunk();
entity.setDocumentId(documentId);
entity.setChunkIndex(draft.chunkIndex());
entity.setHeadingPath(draft.headingPath());
entity.setContent(draft.content());
entity.setContentHash(draft.contentHash());
entity.setTokenCount(draft.tokenCount());
entity.setVectorStatus(
        KnowledgeChunkVectorStatus.PENDING.getCode());
```

然后调用 04 已完成的：

```java
knowledgeChunkMapper.batchInsert(chunks);
```

完整关系：

```text
KnowledgeDocument.content
        ↓
MarkdownChunker
        ↓
List<KnowledgeChunkDraft>
        ↓ 06 阶段转换
List<KnowledgeChunk>
        ↓
KnowledgeChunkMapper.batchInsert
        ↓
MySQL knowledge_chunk
```

本阶段不提前写这段持久化，是为了让每一步都能独立验证。

---

## 二十三、与 06 向量化阶段如何衔接

06 阶段向 EmbeddingModel 发送的文本不应只有正文，建议构造：

```java
String embeddingText = "知识路径："
        + draft.headingPath()
        + "\n\n"
        + draft.content();
```

Redis VectorStore 的 metadata 至少包含：

```text
documentId
chunkId
chunkIndex
headingPath
sourceType
```

注意两种内容的区别：

```text
MySQL content：尽量保存清洗后的真实片段正文
Embedding input：可以加标题路径，帮助模型理解语义
```

不要为了 Embedding 方便，把无法还原的提示词污染到原始文档字段里。

---

## 二十四、你需要掌握的编程思想

### 24.1 单一职责

错误设计：

```text
一个类同时清洗、解析、切片、Hash、入库、向量化
```

当前设计：

```text
Cleaner 只清洗
Parser 只解析结构
Splitter 只控制 Token 窗口
Chunker 只编排
Hasher 只做稳定 Hash
```

### 24.2 纯计算与外部 IO 分离

纯计算特点：

```text
相同输入 -> 相同输出
不访问数据库
不访问网络
不读取当前用户上下文
```

这样的代码最容易测试、复现和优化。

### 24.3 先保正确，再做复杂

第一版没有实现所有 Markdown 语法，但它：

- 输入输出明确；
- 不丢非空正文；
- 代码块不会被标题规则误切；
- 每个 Token 窗口大小可验证；
- 所有规则都有测试。

比一个功能很多但无法证明正确的切片器更可靠。

### 24.4 防御式编程

配置校验、最大 chunk 数量、空值校验都属于防御式编程。

它不是“不相信用户”，而是承认：

- 配置可能写错；
- 文件可能异常；
- 调用方可能漏传；
- 未来维护者可能不了解隐含约束。

让错误尽早、明确地暴露，比在 Embedding 阶段产生模糊异常更好。

### 24.5 可替换性

当前 Token 实现使用 JTokkit。未来如果替换成 `bge-m3` 官方 tokenizer，只需要替换 `TokenWindowSplitter`，不需要改：

- Controller；
- 文档状态机；
- Mapper；
- `MarkdownChunker` 的业务调用方式；
- 后续错题讲解。

这就是稳定边界带来的价值。

---

## 二十五、本阶段验收清单

### 25.1 代码验收

- [ ] `RagProperties` 包含三个切片参数。
- [ ] `KnowledgeChunkDraft` 使用不可变 record。
- [ ] Cleaner 统一 Windows/Linux 换行。
- [ ] Cleaner 删除文档开头 BOM。
- [ ] Cleaner 不破坏代码块内部格式。
- [ ] Parser 能识别 1 到 6 级 ATX 标题。
- [ ] Parser 能构建正确的父子标题路径。
- [ ] Parser 不会把代码块中的 `#` 当标题。
- [ ] TXT 使用文档标题作为默认路径。
- [ ] 超长章节按 Token 拆分。
- [ ] 相邻窗口包含配置的 overlap。
- [ ] 所有 chunk 的 Token 数不超过上限。
- [ ] `chunkIndex` 从 0 连续递增。
- [ ] 相同输入产生相同 Hash 和相同列表。
- [ ] 超过最大 chunk 数时明确失败。

### 25.2 测试验收

- [ ] `KnowledgeTextCleanerTest` 通过。
- [ ] `MarkdownSectionParserTest` 通过。
- [ ] `TokenWindowSplitterTest` 通过。
- [ ] `MarkdownChunkerTest` 通过。
- [ ] `mvn test` 全量通过。
- [ ] 测试不需要 MySQL、Redis、Docker 和 API Key。

### 25.3 理解验收

你应该能用自己的话回答：

1. 为什么不能把整份文档只生成一个向量？
2. 为什么先按标题切，再按 Token 切？
3. Token 与字符有什么区别？
4. overlap 解决什么问题，又带来什么代价？
5. 为什么代码块需要状态机处理？
6. 为什么 `overlap` 必须小于 `chunkSize`？
7. 为什么 Draft 不直接使用数据库 Entity？
8. 为什么 Hash 要包含标题路径？
9. 为什么单元测试不启动 Spring？
10. 当前算法有哪些明确限制？

---

## 二十六、完成后发给我什么

完成后告诉我：

```text
1. 我完成了 RAG 05 文档代码
2. 新建和修改的文件列表
3. 四组专项测试结果
4. mvn test 的最终结果
5. 是否调整过默认切片参数
6. 哪一段代码或原理还不理解
```

我会重点 Review：

- Fence 状态是否会错误退出；
- 标题路径是否会残留旧层级；
- 滑动窗口是否可能死循环；
- 最后一个窗口是否重复生成；
- Token 上限是否真的成立；
- Hash 输入是否稳定；
- 是否错误丢弃短内容；
- 是否让切片器依赖了数据库或外部 API；
- 测试是否覆盖 Markdown 代码块和边界配置。

---

## 二十七、当前只做这些

现在按顺序完成：

```text
1. 修改 RagProperties 与 application.yml
2. 添加 JTokkit 直接依赖
3. 创建 KnowledgeChunkDraft
4. 创建 MarkdownSection
5. 创建 KnowledgeTextCleaner 并先跑测试
6. 创建 MarkdownSectionParser 并先跑测试
7. 创建 TokenWindowSplitter 并先跑测试
8. 创建 MarkdownChunker
9. 完成 MarkdownChunkerTest
10. 执行完整 mvn test
11. 把结果交给我 Code Review
```

当前不要做：

- 不写上传接口；
- 不操作 `KnowledgeDocumentService` 状态；
- 不调用 `KnowledgeChunkMapper`；
- 不调用 Embedding API；
- 不向 Redis 写向量；
- 不创建异步线程池；
- 不接前端；
- 不接错题本和面试；
- 不提前编写 06 阶段代码。

先把“同一份文档可以稳定地产生高质量 chunk”这件事做对。06 阶段才会把这条纯处理管道接入 MySQL、Embedding 和 Redis。
