# 专题 01：Java 字符串、字符编码与文本处理

> 适用阶段：RAG 05“Markdown 清洗与智能切片”的前置补充。
>
> 项目路径：`E:\java-code\ZhiMian`
>
> 学习目标：不靠死记 API，真正理解字符、字节、编码、转义、`String`、`StringUtils`、正则表达式、文本规范化和 Hash 之间的关系。

---

## 一、为什么 RAG 项目里突然出现大量字符串处理

普通 CRUD 中，我们经常把字符串当成一个普通字段：

```java
user.setUsername(dto.getUsername());
```

但 RAG 的输入不是几个简单字段，而是整篇 Markdown/TXT 文档。系统需要把“不稳定的外部文本”转换为“稳定的内部数据”：

```text
文件字节
    ↓ 按字符编码解码
Java String
    ↓ 换行、BOM、空白规范化
清洗后的文本
    ↓ 标题、代码块、段落解析
语义章节
    ↓ Token 切片
知识 chunk
    ↓ UTF-8 字节 + SHA-256
稳定 Hash
```

只要其中一层理解错误，就可能出现：

- 中文乱码；
- 标题识别失败；
- Windows 和 Linux 得到不同 Hash；
- 代码块被错误切开；
- `split()` 少了一行；
- 正则表达式明明看起来正确却匹配不到；
- 重复文档无法去重；
- 相同文档重新导入后产生不同向量数据。

所以字符串处理不是 RAG 的边角工作，而是知识进入系统的第一道数据质量关。

---

## 二、先建立完整的文本模型

### 2.1 字节、字符、字符串不是同一个东西

先记住三层：

```text
字节 byte：计算机存储和网络传输的二进制数据
字符 character：人理解的文字或符号
字符串 String：多个字符组成的文本序列
```

磁盘和网络里没有真正的“Java”或“智面”，只有字节：

```text
11100110 10011001 10111010 ...
```

程序必须使用某种字符编码，才能在字节和文字之间转换：

```text
文字 --编码 encode--> 字节
字节 --解码 decode--> 文字
```

### 2.2 字符编码是什么

字符编码规定“字符与字节如何对应”。常见编码：

| 编码 | 特点 |
|---|---|
| ASCII | 只能表达基础英文、数字和符号 |
| GBK | 传统中文编码，兼容性场景仍会遇到 |
| UTF-8 | 互联网最常用，兼容 ASCII，中文通常占 3 字节 |
| UTF-16 | Java `String` 内部代码单元模型相关，常见字符通常占 1 或 2 个 `char` |

智面项目统一使用 UTF-8 处理文件、HTTP、数据库和 Hash 输入。

### 2.3 编码和解码为什么必须一致

正确过程：

```text
“智面” --UTF-8 编码--> 一组字节 --UTF-8 解码--> “智面”
```

错误过程：

```text
“智面” --UTF-8 编码--> 一组字节 --GBK 解码--> 乱码
```

Java 中应明确字符集：

```java
import java.nio.charset.StandardCharsets;

byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
```

不要依赖系统默认字符集：

```java
// 不推荐：换一台机器后默认编码可能不同
byte[] bytes = text.getBytes();
String restored = new String(bytes);
```

### 2.4 Java 的 `char` 不一定等于一个完整字符

Java 的 `char` 是 16 位 UTF-16 代码单元。很多常见中文字符可以放进一个 `char`：

```java
char value = '智';
```

但某些 Emoji、生僻字需要两个 `char` 共同表达。

例如：

```java
String text = "A😀B";

System.out.println(text.length());
System.out.println(text.codePointCount(0, text.length()));
```

可能得到：

```text
4  // UTF-16 char 数量
3  // Unicode Code Point 数量
```

因此：

```java
text.length()
```

表示 UTF-16 代码单元数量，不严格等于用户肉眼看到的字符数量，更不等于 Token 数。

### 2.5 四个容易混淆的长度

对同一段文本可能有四种“长度”：

```text
String.length()：UTF-16 char 数量
codePointCount()：Unicode 码点数量
UTF-8 bytes.length：存储/传输字节数
tokenCount：模型 tokenizer 处理后的 Token 数
```

在智面中的用途：

| 长度 | 用途 |
|---|---|
| `length()` | 普通字段长度校验、截断错误消息 |
| Code Point 数 | 需要按人类字符处理 Emoji 时使用 |
| UTF-8 字节数 | 文件大小、网络大小、Hash 输入 |
| Token 数 | Chunk 大小、模型上下文预算 |

不能用 `content.length()` 代替 Token 数。

---

## 三、Java 字符串字面量与转义字符

### 3.1 为什么需要转义

Java 使用双引号标记字符串：

```java
String text = "智面";
```

如果字符串本身需要包含双引号，直接写会破坏语法：

```java
// 错误
String text = "他说："你好"";
```

需要使用反斜杠转义：

```java
String text = "他说：\"你好\"";
```

### 3.2 常见转义字符

| Java 写法 | 实际字符 | 含义 |
|---|---|---|
| `\n` | LF | 换行 |
| `\r` | CR | 回车 |
| `\t` | Tab | 制表符 |
| `\\` | `\` | 反斜杠 |
| `\"` | `"` | 双引号 |
| `\'` | `'` | 单引号 |
| `\uFEFF` | BOM 字符 | Unicode 转义 |

注意：源码中的两个字符 `\` 和 `n`：

```java
"\\n"
```

表示普通文本：

```text
\n
```

而：

```java
"\n"
```

表示一个真正的换行字符。

### 3.3 `char` 与 `String` 的引号不同

```java
char oneCharacter = '\n';
String oneCharacterText = "\n";
```

单引号表示 `char`，双引号表示 `String`。

下面是错误的：

```java
// char 只能表示一个 UTF-16 代码单元
char text = 'Java';
```

### 3.4 Unicode 转义

```java
private static final char BOM = '\uFEFF';
```

`FEFF` 是十六进制 Unicode 编号。它表示一个真实字符，不是六个普通字符。

可以验证：

```java
System.out.println((int) BOM);       // 十进制 65279
System.out.println(Integer.toHexString(BOM)); // feff
```

### 3.5 Windows 路径为什么需要两个反斜杠

Java 字符串中的 `\` 是转义起点，所以：

```java
String path = "E:\\java-code\\ZhiMian";
```

运行时字符串才是：

```text
E:\java-code\ZhiMian
```

使用 `Path` 时更推荐：

```java
Path path = Path.of("E:\\", "java-code", "ZhiMian");
```

业务代码不应手工拼接系统路径分隔符。

---

## 四、换行符到底是什么

### 4.1 换行不是“看起来换了一行”这么简单

常见换行：

```text
Windows：CRLF，即 \r\n
Linux：LF，即 \n
旧式 Mac：CR，即 \r
```

IDE 会把它们都显示成换行，所以肉眼看不出区别。但对程序来说它们是不同字符。

### 4.2 为什么影响 Hash

下面两段肉眼看起来相同：

```text
Java
Redis
```

实际可能分别是：

```java
"Java\r\nRedis"
"Java\nRedis"
```

它们的字节不同，因此 SHA-256 也不同。

### 4.3 正确的换行规范化顺序

```java
String normalized = content
        .replace("\r\n", "\n")
        .replace('\r', '\n');
```

必须先处理两个字符组成的 `\r\n`，再处理剩余单独的 `\r`。

如果反过来：

```java
content.replace("\r", "\n")
       .replace("\r\n", "\n");
```

原来的 `\r\n` 在第一步会变成 `\n\n`，第二步已经无法恢复，文档中会多出空行。

### 4.4 `System.lineSeparator()` 适合什么场景

```java
String separator = System.lineSeparator();
```

它返回当前操作系统偏好的换行符。

适合：

- 生成给本机用户查看的普通文本文件；
- 控制台输出。

不适合智面的内部规范化结果，因为内部数据需要跨系统保持一致。RAG 清洗统一使用 `\n`。

---

## 五、`String` 为什么叫不可变对象

### 5.1 所有修改方法都返回新字符串

```java
String original = "  Java  ";
original.strip();

System.out.println(original);
```

`original` 仍然包含空白，因为 `strip()` 没有修改原对象。

正确写法：

```java
String cleaned = original.strip();
```

同样适用于：

```java
replace()
replaceFirst()
toUpperCase()
toLowerCase()
substring()
strip()
```

### 5.2 为什么设计成不可变

不可变带来的好处：

- 可以安全共享；
- 适合作为 Map 的 key；
- Hash 值不会因为内容变化失效；
- 多线程读取时更简单；
- 字符串常量池可以复用对象。

代价是频繁拼接会创建很多临时对象，所以大量循环拼接要使用 `StringBuilder`。

### 5.3 `==` 与 `equals()`

```java
String first = new String("Java");
String second = new String("Java");
```

```java
first == second
```

比较的是是否为同一个对象，结果通常是 `false`。

```java
first.equals(second)
```

比较字符串内容，结果是 `true`。

字符串内容比较应使用：

```java
Objects.equals(first, second)
```

或者在常量已知时：

```java
"MARKDOWN".equals(sourceType)
```

后一种写法即使 `sourceType == null` 也不会空指针。

---

## 六、`null`、空字符串和空白字符串

### 6.1 四种状态

```java
String a = null;
String b = "";
String c = "   ";
String d = "\n\t";
```

它们不同：

```text
null：没有 String 对象
""：有对象，但长度为 0
"   "：有字符，但全是空格
"\n\t"：有字符，但全是空白符
```

### 6.2 `isEmpty()`

```java
text.isEmpty()
```

只判断长度是不是 0。

```text
""      -> true
"   "   -> false
```

调用前必须保证 `text != null`。

### 6.3 `isBlank()`

```java
text.isBlank()
```

判断字符串是否为空，或者是否全部由空白字符组成。

```text
""      -> true
"   "   -> true
"\n\t"  -> true
"Java"  -> false
```

同样不能直接对 `null` 调用。

### 6.4 Spring `StringUtils.hasText()`

```java
StringUtils.hasText(text)
```

表示：

```text
不为 null
并且长度不为 0
并且至少存在一个非空白字符
```

结果：

| 输入 | `hasText` |
|---|---:|
| `null` | `false` |
| `""` | `false` |
| `"   "` | `false` |
| `"\n"` | `false` |
| `" Java "` | `true` |

所以 Cleaner 开头使用：

```java
if (!StringUtils.hasText(content)) {
    return "";
}
```

一条判断同时处理 `null`、空字符串和纯空白字符串。

### 6.5 `StringUtils.hasLength()` 与 `hasText()`

```java
StringUtils.hasLength("   ") // true
StringUtils.hasText("   ")   // false
```

`hasLength()` 只在意有没有字符；`hasText()` 还要求存在实际文本。

业务参数校验通常更适合 `hasText()`。

---

## 七、`trim()`、`strip()` 与三个方向

### 7.1 `strip()`

```java
String result = text.strip();
```

删除字符串开头和结尾的 Unicode 空白字符。

### 7.2 `stripLeading()`

```java
String result = text.stripLeading();
```

只删除开头空白。

Cleaner 中使用：

```java
String leftTrimmed = line.stripLeading();
```

目的是识别前面带缩进的代码围栏，但仍保留原始 `line`。

### 7.3 `stripTrailing()`

```java
String result = text.stripTrailing();
```

只删除结尾空白。

普通 Markdown 行使用它，可以保留列表和段落的行首结构，同时清除无意义尾部空格。

### 7.4 `trim()` 为什么不再优先使用

`trim()` 是早期 Java API，主要删除编码值小于等于 32 的字符。`strip()` 使用 Unicode 空白判断，面对国际文本更完整。

Java 11 及以上优先使用：

```text
strip
stripLeading
stripTrailing
isBlank
```

### 7.5 不能随处调用 `strip()`

下面的 Java 代码：

```text
    if (ready) {
        run();
    }
```

如果每一行都调用 `strip()`，缩进会丢失。对于 Markdown 代码块、Python、YAML，缩进甚至可能影响语义。

所以 Cleaner 的规则是：

```text
代码块内：保留原始行
代码块外：只 stripTrailing
整篇结束：最终 strip
```

---

## 八、查询、截取和替换字符串

### 8.1 `charAt()`

```java
char first = text.charAt(0);
```

用于读取某个 UTF-16 位置的 `char`。

必须满足：

```text
0 <= index < text.length()
```

所以 Cleaner 先判断：

```java
if (!normalized.isEmpty() && normalized.charAt(0) == BOM)
```

否则空字符串调用 `charAt(0)` 会抛出 `StringIndexOutOfBoundsException`。

### 8.2 `substring()`

```java
String value = "ABCDE";

value.substring(1);    // BCDE
value.substring(1, 4); // BCD
```

第二种使用左闭右开区间：

```text
[1, 4)
```

包含索引 1，不包含索引 4。

Cleaner 删除 BOM：

```java
normalized = normalized.substring(1);
```

表示舍弃索引 0，从索引 1 保留到末尾。

### 8.3 `startsWith()` 与 `endsWith()`

```java
line.startsWith("```")
filename.endsWith(".md")
```

它们是字面量匹配，不是正则表达式。

```java
"```java".startsWith("```") // true
```

Cleaner 用它判断当前行是否以代码围栏开头。

### 8.4 `contains()`

```java
text.contains("线程池")
```

适合简单的字面量包含判断。它不支持正则，也不忽略大小写。

### 8.5 `indexOf()` 与 `lastIndexOf()`

```java
int index = text.indexOf("[下一题]");
int last = text.lastIndexOf("[下一题]");
```

找不到时返回：

```text
-1
```

项目中的 `AiQuestionExtractor` 使用 `lastIndexOf`，是为了从 AI 回复中取最后一个题目标记。

### 8.6 `replace()` 是字面量替换

```java
text.replace("\r\n", "\n")
```

`replace()` 不把第一个参数当正则，所以处理固定换行符时简单可靠。

### 8.7 `replaceFirst()` 与 `replaceAll()` 使用正则

```java
text.replaceFirst("^[-*]+\\s*", "")
```

```text
replaceFirst：只替换第一个匹配
replaceAll：替换所有匹配
```

它们的第一个参数是正则表达式，因此需要理解双重转义。

---

## 九、`split()` 为什么经常让初学者困惑

### 9.1 `split()` 的参数是正则表达式

```java
String[] parts = text.split(",");
```

逗号不是特殊正则字符，所以看起来像普通分隔。

但：

```java
text.split(".")
```

不是“按句点切”，因为正则里的 `.` 表示任意字符。

按真正句点切应写：

```java
text.split("\\.")
```

### 9.2 为什么正则里的反斜杠要写两次

你希望正则引擎收到：

```text
\.
```

但 Java 字符串本身也使用 `\` 转义，所以源码必须写：

```java
"\\."
```

过程：

```text
Java 源码 "\\."
    ↓ Java 字符串解析
运行时字符串 \.
    ↓ 正则解析
匹配普通句点 .
```

这叫“双重转义”：先通过 Java 字符串语法，再通过正则语法。

### 9.3 `split("\n", -1)` 中的 `-1`

```java
String[] lines = normalized.split("\n", -1);
```

第二个参数叫 `limit`。

当它为 `-1` 时，保留末尾空字符串：

```java
"A\nB\n".split("\n", -1)
```

得到：

```text
["A", "B", ""]
```

如果写：

```java
"A\nB\n".split("\n")
```

默认会丢弃末尾空字符串：

```text
["A", "B"]
```

文本清洗希望先完整保留结构，再由明确规则决定删什么，所以使用 `-1`。

### 9.4 分隔符来自用户时怎么办

如果分隔符是普通文本而不是正则，应该使用：

```java
Pattern.quote(delimiter)
```

例如：

```java
String delimiter = ".";
String[] parts = text.split(Pattern.quote(delimiter));
```

这样用户输入的 `.`, `|`, `*`, `+` 不会被当作正则语法。

---

## 十、正则表达式入门：结合标题解析器学习

### 10.1 正则表达式解决什么问题

普通方法适合固定文本：

```java
line.startsWith("# ")
```

但 Markdown 标题可能有 1 到 6 个 `#`，还可能有前导空格。正则适合描述一类文本结构。

智面的标题模式：

```java
Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*$");
```

### 10.2 去掉 Java 字符串转义后，正则真正看到什么

运行时正则是：

```regex
^\s{0,3}(#{1,6})\s+(.+?)\s*$
```

拆解：

| 正则 | 含义 |
|---|---|
| `^` | 一行开始 |
| `\s{0,3}` | 0 到 3 个空白字符 |
| `(#{1,6})` | 1 到 6 个 `#`，第 1 个捕获组 |
| `\s+` | 至少一个空白字符 |
| `(.+?)` | 标题正文，第 2 个捕获组 |
| `\s*` | 0 个或多个空白字符 |
| `$` | 一行结束 |

### 10.3 数量词

| 写法 | 含义 |
|---|---|
| `*` | 0 次或多次 |
| `+` | 1 次或多次 |
| `?` | 0 次或 1 次；跟在数量词后还可表示非贪婪 |
| `{n}` | 正好 n 次 |
| `{n,m}` | n 到 m 次 |

例如：

```regex
#{1,6}
```

匹配 1 到 6 个 `#`。

### 10.4 捕获组

圆括号会保存匹配内容：

```java
Matcher matcher = HEADING_PATTERN.matcher("## JVM");

if (matcher.matches()) {
    String hashes = matcher.group(1); // ##
    String title = matcher.group(2);  // JVM
}
```

`group(0)` 表示完整匹配：

```text
## JVM
```

### 10.5 `matches()` 与 `find()`

```java
matcher.matches()
```

要求整段字符串符合模式。

```java
matcher.find()
```

只要字符串中某一部分符合即可，并且可以继续寻找下一处。

标题解析器处理的是“一整行是否为标题”，所以使用 `matches()`。

### 10.6 贪婪与非贪婪

```regex
.+
```

默认尽可能多匹配，叫贪婪。

```regex
.+?
```

尽可能少匹配，叫非贪婪。

正则中的行为还会受到后续模式影响。不要只背“多”和“少”，要从整条表达式理解它最终如何匹配。

### 10.7 什么时候不要使用正则

正则适合局部、规则明确的文本模式，不适合承担完整 Markdown 语法解析。

例如“`#` 是否处在代码块里”需要记住之前是否进入代码块。这是状态问题，单独匹配当前行的正则不知道历史。

所以智面使用：

```text
状态机：判断当前在代码块内还是外
正则：在代码块外识别标题行
```

---

## 十一、`StringBuilder` 与文本拼接

### 11.1 为什么循环中不用 `+`

```java
String result = "";
for (String line : lines) {
    result = result + line + "\n";
}
```

`String` 不可变，每次拼接都可能创建新对象并复制旧内容。文档很大时，会产生大量临时对象。

推荐：

```java
StringBuilder result = new StringBuilder();
for (String line : lines) {
    result.append(line).append('\n');
}
String finalText = result.toString();
```

### 11.2 为什么 Cleaner 传入初始容量

```java
StringBuilder result = new StringBuilder(normalized.length());
```

清洗后的文本通常不会比原文大很多，因此原文长度是合理的初始容量估计，可以减少扩容和复制。

这不是必须的正确性代码，只是成本很低的小优化。

### 11.3 `append(char)` 与 `append(String)`

```java
builder.append('\n');  // 添加一个 char
builder.append("\n"); // 添加一个长度为 1 的 String
```

两者结果相同。表达单个字符时使用 `char` 更直接。

### 11.4 为什么 `appendLine()` 先判断空

```java
private void appendLine(StringBuilder builder, String line) {
    if (!builder.isEmpty()) {
        builder.append('\n');
    }
    builder.append(line);
}
```

第一次添加行时不需要前导换行；之后每行前添加一个换行。

这样结果是：

```text
第一行\n第二行\n第三行
```

而不是：

```text
\n第一行\n第二行\n第三行
```

### 11.5 `StringBuilder` 不是线程安全容器

一个方法内部创建的局部 `StringBuilder` 只被当前线程使用，非常安全。

不要把它放成 Spring 单例组件的可变成员字段：

```java
@Component
public class WrongCleaner {
    private final StringBuilder builder = new StringBuilder();
}
```

多个请求会同时修改它，造成内容串线。正确做法是在每次方法调用里新建。

---

## 十二、文本块和字符串格式化

### 12.1 Java 文本块

Java 17 可以使用三个双引号表达多行文本：

```java
String markdown = """
        # Java

        ## JVM
        JVM 负责执行字节码。
        """;
```

它比大量 `\n` 更适合：

- 单元测试里的 Markdown；
- Prompt 模板；
- 多行示例数据。

### 12.2 文本块会处理公共缩进

Java 会根据关闭符号和内容计算附带缩进，所以代码里的视觉缩进不一定进入最终字符串。

测试时不要仅凭源码外观猜测，可以打印：

```java
System.out.println(markdown.replace(" ", "·"));
```

把空格临时显示成点，便于检查。

### 12.3 `String.format()`

```java
String message = String.format(
        "文档 %s 被切成 %d 个片段",
        title,
        chunkCount
);
```

常见占位符：

| 占位符 | 含义 |
|---|---|
| `%s` | 字符串 |
| `%d` | 十进制整数 |
| `%.2f` | 保留两位小数 |
| `%n` | 平台换行符 |

内部知识文本统一换行时不要使用 `%n`，因为它随系统变化，应明确使用 `\n`。

### 12.4 `formatted()`

Java 15 之后可以写：

```java
String message = "文档 %s 被切成 %d 个片段"
        .formatted(title, chunkCount);
```

它与 `String.format()` 思路相同，链式表达更自然。

### 12.5 RAG Prompt 格式化要注意什么

不要把外部知识和系统指令混成无法区分的一段文本。后续可使用明确边界：

```java
String context = """
        <knowledge>
        %s
        </knowledge>
        """.formatted(retrievedContent);
```

但标签只是结构提示，不是安全沙箱。后续仍要防范知识文档中的 Prompt Injection。

---

## 十三、BOM、不可见字符与空白字符

### 13.1 BOM 是什么

BOM 全称 Byte Order Mark。UTF-8 并不需要字节序，但有些编辑器仍会在文件开头写入 UTF-8 BOM。

Java 读取后，它可能成为：

```java
'\uFEFF'
```

人眼看不到，但：

```java
text.startsWith("#")
```

可能返回 `false`，因为真正的第一个字符是 BOM。

### 13.2 为什么只删除开头 BOM

```java
if (!normalized.isEmpty() && normalized.charAt(0) == BOM) {
    normalized = normalized.substring(1);
}
```

BOM 正常只出现在文件开头。如果直接：

```java
normalized.replace("\uFEFF", "")
```

会删除正文中所有同值字符，清洗范围过大。

文本规范化的基本原则是：只修改确定属于格式噪音的内容。

### 13.3 什么叫空白字符

空白不只有普通空格，还可能包含：

- Tab；
- 换行；
- 回车；
- 某些 Unicode 空格。

所以：

```java
line.equals("")
```

不能判断一行是不是“视觉空白”。应该使用：

```java
line.isBlank()
```

---

## 十四、文本规范化与 Hash 的关系

### 14.1 什么叫规范化

规范化是把“意义相同但表示形式不同”的输入转换成统一形式。

智面当前执行：

```text
CRLF/CR -> LF
删除首部 BOM
删除代码块外行尾空白
收敛过量空行
整篇 strip
```

规范化后再计算 Hash，相同资料更容易得到相同结果。

### 14.2 规范化必须稳定

稳定意味着：

```text
normalize(normalize(text)) == normalize(text)
```

这种性质叫幂等性。

如果每清洗一次都会继续改变内容，重复导入和故障重试就无法稳定。

可以写测试：

```java
String once = cleaner.clean(input);
String twice = cleaner.clean(once);

assertEquals(once, twice);
```

### 14.3 SHA-256 对微小变化非常敏感

```text
Java\nRedis
Java\r\nRedis
Java \nRedis
```

这三者字节不同，Hash 会完全不同。

所以 `KnowledgeContentHasher` 先规范化，再执行：

```java
normalized.getBytes(StandardCharsets.UTF_8)
```

最后计算 SHA-256。

### 14.4 为什么不做过度规范化

下面这些做法很危险：

```text
删除所有空格
删除所有标点
把所有内容转小写
删除所有 Markdown 符号
删除代码缩进
```

因为：

```text
a + +b
a++ + b
```

删除空格后可能变成难以区分的代码。

知识清洗的目标是去格式噪音，不是改写知识。

### 14.5 Unicode Normalizer 是什么

Java 提供：

```java
java.text.Normalizer
```

某些视觉相同字符可以由不同 Unicode 序列表示，`Normalizer` 能转换为 NFC/NFD/NFKC/NFKD 等形式。

示例：

```java
String normalized = Normalizer.normalize(
        text,
        Normalizer.Form.NFC
);
```

智面第一版暂时不强行加入这一规则，因为知识文档主要是中英文技术文本，且兼容性规范化可能改变全角符号等内容。是否加入应先增加真实样本和测试。

---

## 十五、状态机：字符串处理中很重要的编程思想

### 15.1 为什么一行正则不够

看到下面一行：

```text
# 数据库初始化脚本
```

它可能是 Markdown 标题，也可能是 shell 代码块中的注释。

仅看当前行无法判断，必须知道前面是否已经进入代码块。

### 15.2 Cleaner 的两个状态

```text
OUTSIDE_FENCE：代码块外
INSIDE_FENCE：代码块内
```

虽然代码用 `boolean inCodeFence` 表示，但思维上它是状态机。

状态变化：

```text
OUTSIDE_FENCE
    遇到 ``` 或 ~~~
        ↓
INSIDE_FENCE
    遇到匹配的关闭围栏
        ↓
OUTSIDE_FENCE
```

### 15.3 状态决定同一行如何处理

```text
代码块外的空白行：允许压缩
代码块内的空白行：必须保留

代码块外的行尾空白：可以清理
代码块内的行尾空白：保守保留
```

这叫“同一输入在不同状态下采用不同规则”。

以后你会在很多地方看到状态机：

- 面试状态；
- 文档导入状态；
- 订单状态；
- SSE 连接状态；
- 消息消费状态；
- 文件解析状态。

---

## 十六、逐段理解 `KnowledgeTextCleaner`

### 16.1 入口保护

```java
if (!StringUtils.hasText(content)) {
    return "";
}
```

目的：将 `null`、空串和纯空白输入统一成空字符串，简化后续处理。

### 16.2 建立稳定输入

```java
String normalized = content
        .replace("\r\n", "\n")
        .replace('\r', '\n');
```

目的：消除操作系统换行差异。

### 16.3 删除文件级隐藏字符

```java
if (!normalized.isEmpty() && normalized.charAt(0) == BOM) {
    normalized = normalized.substring(1);
}
```

目的：防止 BOM 干扰第一行标题和 Hash。

### 16.4 保留完整行结构

```java
String[] lines = normalized.split("\n", -1);
```

目的：逐行处理，同时保留结尾空行信息。

### 16.5 创建方法级状态

```java
boolean inCodeFence = false;
String fenceMarker = null;
int consecutiveBlankLines = 0;
```

这些都是局部变量，每次 `clean()` 调用都有独立状态，不会在不同请求之间共享。

### 16.6 代码块内直接保留

```java
if (inCodeFence) {
    appendLine(result, line);
    if (isClosingFence(leftTrimmed, fenceMarker)) {
        inCodeFence = false;
        fenceMarker = null;
    }
    continue;
}
```

`continue` 表示立即结束当前循环，避免代码行继续进入普通文本清洗逻辑。

这里不能只用 `startsWith(fenceMarker)`：```` ```not-closing ```` 这样的代码内容虽然具有相同前缀，却不是合法关闭行。`isClosingFence` 还会检查围栏长度不少于开始围栏，并且围栏后只剩空白。

### 16.7 代码块外执行保守清洗

```java
String cleanedLine = line.stripTrailing();
```

只清理尾部，不破坏行首结构。

### 16.8 空行计数

```java
if (cleanedLine.isBlank()) {
    consecutiveBlankLines++;
    if (consecutiveBlankLines > MAX_CONSECUTIVE_BLANK_LINES) {
        continue;
    }
}
else {
    consecutiveBlankLines = 0;
}
```

连续第三个空行开始跳过；遇到正文后重新计数。

### 16.9 输出边界清理

```java
return result.toString().strip();
```

删除整篇文档首尾空白，但保留正文内部已经整理好的结构。

---

## 十七、常见错误清单

### 17.1 忘记接收 String 方法返回值

错误：

```java
content.strip();
return content;
```

正确：

```java
return content.strip();
```

### 17.2 对可能为 null 的字符串调用方法

错误：

```java
if (content.isBlank()) {
}
```

当 `content == null` 时会空指针。

正确：

```java
if (!StringUtils.hasText(content)) {
}
```

### 17.3 使用 `==` 比较内容

错误：

```java
if (sourceType == "MARKDOWN") {
}
```

正确：

```java
if ("MARKDOWN".equals(sourceType)) {
}
```

### 17.4 把 `split()` 当普通字符串分隔

错误：

```java
text.split(".")
```

正确：

```java
text.split("\\.")
```

或者：

```java
text.split(Pattern.quote("."))
```

### 17.5 换行替换顺序错误

必须先 `\r\n`，后 `\r`。

### 17.6 循环中大量使用字符串 `+`

文档级循环使用 `StringBuilder`。

### 17.7 清洗代码块缩进

代码块内不要对每行调用 `strip()`。

### 17.8 用字符数代替 Token 数

`length()` 只能做普通长度判断，不能控制 Embedding 窗口。

### 17.9 使用默认字符集

Hash、文件转换和网络文本要明确使用 `StandardCharsets.UTF_8`。

### 17.10 正则转义层数错误

先写出希望正则引擎看到的模式，再转换成 Java 字符串。

例如正则需要：

```regex
\s+
```

Java 源码写：

```java
"\\s+"
```

---

## 十八、建议你亲手完成的实验

这些实验不要复制答案，先预测结果，再运行。

### 实验 1：空字符串与空白字符串

```java
String[] values = {null, "", "   ", "\n", " Java "};
```

分别判断：

```text
StringUtils.hasText
isEmpty（先判 null）
isBlank（先判 null）
```

把结果做成表格。

### 实验 2：检查隐藏换行

```java
String windows = "Java\r\nRedis";
String linux = "Java\nRedis";
```

比较：

```java
windows.equals(linux)
windows.length()
linux.length()
```

规范化后再比较。

### 实验 3：观察 UTF-8 字节

```java
String text = "A智😀";
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
```

打印：

```text
String.length()
codePointCount
UTF-8 bytes.length
每个 byte 的十六进制值
```

### 实验 4：理解 `split` 的 limit

对下面文本分别使用 `split("\n")` 和 `split("\n", -1)`：

```java
String text = "A\nB\n";
```

打印数组长度和每个元素。

### 实验 5：理解双重转义

分别尝试：

```java
"a.b".split(".")
"a.b".split("\\.")
"a.b".split(Pattern.quote("."))
```

解释三个结果为什么不同。

### 实验 6：验证 String 不可变

```java
String original = "  Java  ";
String cleaned = original.strip();
```

打印两个变量，并判断是不是同一个引用、内容是否相同。

### 实验 7：验证 Cleaner 幂等

```java
String once = cleaner.clean(input);
String twice = cleaner.clean(once);
```

断言：

```java
assertEquals(once, twice);
```

### 实验 8：构造代码块边界

准备包含以下内容的 Markdown：

- 普通标题；
- Java 代码块；
- 代码块中的 `#`；
- 连续四个空行；
- Windows 换行；
- 文件开头 BOM。

运行 Cleaner 后逐行打印，说明每一处为什么保留或删除。

---

## 十九、建议补充的单元测试

在现有 `KnowledgeTextCleanerTest` 中，你可以增加这些测试，但先自己写：

```text
1. null 返回空字符串
2. 纯空白返回空字符串
3. Windows CRLF 被转换为 LF
4. 单独 CR 被转换为 LF
5. 开头 BOM 被删除
6. 正文中的普通字符不被删除
7. 代码块内空行和尾部空格保留
8. 代码块外多余空行被压缩
9. 三反引号 Fence 能正确开关
10. 三波浪线 Fence 能正确开关
11. 四反引号 Fence 能记录完整长度
12. Cleaner 满足幂等性
```

测试命名示例：

```java
@Test
void shouldNormalizeWindowsLineEndings() {
}

@Test
void shouldPreserveWhitespaceInsideCodeFence() {
}

@Test
void shouldBeIdempotent() {
}
```

命名结构：

```text
should + 预期行为 + 条件
```

---

## 二十、如何选择 String 工具

遇到文本需求时，可以按下面顺序判断。

### 判断是否有有效文本

```java
StringUtils.hasText(value)
```

### 删除首尾空白

```java
value.strip()
```

### 只删除一侧空白

```java
value.stripLeading()
value.stripTrailing()
```

### 判断前缀、后缀、包含

```java
startsWith
endsWith
contains
```

### 固定文本替换

```java
replace
```

### 正则替换

```java
replaceFirst
replaceAll
```

### 固定规则分割

```java
split
```

记住 `split` 参数仍是正则。

### 循环拼接大量文本

```java
StringBuilder
```

### 多行固定模板

```java
Java text block
```

### 字节与字符串互转

```java
StandardCharsets.UTF_8
```

### 复杂语法结构

优先使用成熟 Parser，或者建立明确状态机，不要试图用一条巨大正则解决全部问题。

---

## 二十一、学习验收问题

学完后，你应该能不看文档回答：

1. 字节、字符、字符串有什么区别？
2. 编码和解码为什么必须一致？
3. 为什么 `String.length()` 不一定等于肉眼字符数？
4. 字符数、字节数、Token 数分别用于什么场景？
5. `\n`、`\r`、`\r\n` 分别是什么？
6. 为什么换行规范化要先替换 `\r\n`？
7. `"\n"` 和 `"\\n"` 有什么区别？
8. 为什么 Windows 路径中的反斜杠要转义？
9. `null`、`""`、`"   "` 有什么区别？
10. `isEmpty`、`isBlank`、`StringUtils.hasText` 有什么区别？
11. `trim` 和 `strip` 有什么区别？
12. 为什么代码块内不能随便调用 `strip`？
13. String 为什么不可变？
14. 为什么循环拼接文档使用 `StringBuilder`？
15. `split("\n", -1)` 中 `-1` 有什么作用？
16. 为什么按句点分割要写 `split("\\.")`？
17. `matches()` 与 `find()` 有什么区别？
18. 为什么代码 Fence 需要状态机而不是只用正则？
19. BOM 会对标题识别和 Hash 造成什么影响？
20. 文本清洗为什么要保守、稳定、幂等？

---

## 二十二、本专题与 05 的对应关系

回到 05 文档时，你会发现每段代码都有明确知识来源：

| 05 代码 | 本专题知识 |
|---|---|
| `StringUtils.hasText(content)` | null、空串、空白串 |
| `replace("\r\n", "\n")` | 换行规范化 |
| `char BOM = '\uFEFF'` | Unicode 转义与 BOM |
| `charAt(0)` | 索引与边界检查 |
| `substring(1)` | 左闭右开和删除首字符 |
| `split("\n", -1)` | 正则分割与 limit |
| `stripLeading()` | 识别缩进后的 Fence |
| `stripTrailing()` | 保守清理普通文本行 |
| `isBlank()` | 识别视觉空行 |
| `StringBuilder` | 高效构造大文本 |
| `startsWith(fenceMarker)` | 固定前缀判断 |
| `Pattern`/`Matcher` | Markdown 标题识别 |
| `\\s` | Java 字符串与正则双重转义 |
| `StandardCharsets.UTF_8` | 稳定字节转换与 Hash |
| `inCodeFence` | 文本解析状态机 |

建议学习顺序：

```text
先读本专题第二到九章
    ↓
重新读 KnowledgeTextCleaner
    ↓
完成 Cleaner 测试
    ↓
读本专题第十和十五章
    ↓
编写 MarkdownSectionParser
    ↓
继续 05 的 TokenWindowSplitter
```

不需要一次背完所有 API。你真正需要的是：看到一行字符串代码时，能说清它接收什么、返回什么、是否使用正则、是否可能遇到 null、是否会丢失原始信息。

---

## 二十三、最后总结

把本专题压缩成六条原则：

```text
1. 磁盘和网络保存的是字节，String 表达的是解码后的文本。
2. 所有字节转换都明确使用 UTF-8。
3. String 不可变，调用清洗方法后必须接收返回值。
4. null、空字符串、空白字符串要分别理解，业务校验常用 hasText。
5. 正则要经历 Java 字符串和正则引擎两层解析，注意双重转义。
6. 文本清洗要保守、稳定、幂等，不能为了整齐而损坏知识。
```

掌握这些后，05 的 Cleaner 和 Parser 就不再是一堆零散字符操作，而是一条清晰的数据处理管道。
