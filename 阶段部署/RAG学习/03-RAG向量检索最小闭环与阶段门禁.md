# 6.6.1 RAG 向量检索最小闭环与阶段门禁

> 本任务是智面 RAG 阶段的第一个开发单元。
>
> 本阶段暂时不做文档上传、不建知识库管理页面、不接入面试主链路。唯一目标是把“文本向量化 → 写入向量库 → 语义检索 → 验证排序 → 删除测试数据”完整跑通。
>
> 代码仍由你手动编写。每完成一小节就运行对应命令，不要一次写完再统一排错。

---

## 一、阶段门禁结论

### 1.1 结论：可以开始 RAG

当前项目的业务模块已经达到进入 RAG 阶段的条件，不需要为了 RAG 继续返工普通 CRUD。

本次检查结果：

| 检查项 | 当前结果 | 是否阻断 RAG |
|---|---|---|
| 后端编译 | 通过 | 否 |
| 后端自动化测试 | 13 个测试类、49 个测试全部通过 | 否 |
| 前端 TypeScript 与生产构建 | 通过 | 否 |
| 题库/题目详情 | 已实现 | 否 |
| 答题记录/错题本 | 已实现 | 否 |
| 收藏夹/排行榜/仪表盘 | 已实现 | 否 |
| AI 面试/SSE/上下文/报告 | 已实现 | 否 |
| AI 面试题自动沉淀 | 已实现 | 否 |
| Spring AI ChatModel | 已接入 | 否 |
| EmbeddingModel | 尚未配置 | 是，但属于本任务内容 |
| 向量数据库 | 当前 Redis 不支持 | 是，但属于本任务内容 |
| RAG 知识库表 | 尚未创建 | 否，下一开发单元处理 |

因此准确说法是：

> **应用层已经具备进入 RAG 阶段的条件；RAG 专属基础设施尚未具备，需要先完成本任务。**

### 1.2 为什么现有模块已经够用

第一版 RAG 的业务出口是“错题/题目知识库讲解”。它依赖：

1. 能根据 `questionId` 查到题目标题、题干和参考答案；
2. 用户答错后能进入错题本；
3. 已有 `ChatClient` 可以生成自然语言讲解；
4. Redis、异步线程池、异常处理等基础已经存在；
5. 测试框架已经建立。

这些条件当前都已具备。

### 1.3 当前仍存在但不阻断开工的不足

#### 不足一：测试主要是 Mockito 单元测试

当前 49 个后端测试能够验证 Service 业务分支，但没有真实启动 MySQL、Redis 和 DeepSeek。RAG 对外部组件依赖更强，所以从本阶段开始必须补充“显式运行的集成测试”。

这不是要求先停下来补完所有旧模块集成测试，而是要求 RAG 自己不能只 Mock `VectorStore`。

#### 不足二：前端主包有体积提示

Vite 构建成功，但主包超过 500 KB。这属于前端性能优化，不影响后端 RAG 第一阶段。

#### 不足三：当前工作区存在尚未提交的改动

开始 RAG 前强烈建议先把当前基础版本单独 commit。原因不是代码不能运行，而是 RAG 会修改 `pom.xml`、Redis 配置和 SQL；如果基础功能与 RAG 改动混在一个提交里，出现故障时很难回退和比较。

建议形成一个清晰基线：

```powershell
git status
git add 你确认属于当前基础版本的文件
git commit -m "feat: complete core interview application"
```

不要盲目执行 `git add .`，先确认 `.claude/settings.local.json`、本地密钥和临时文件不应提交。

---

## 二、本机最重要的前置问题：普通 Redis 不支持向量检索

本次实际检查结果：

```text
redis_version: 5.0.14.1
os: Windows
MODULE LIST: 空
```

当前 Redis 可以继续执行 String、List、Set、ZSet、Lua、过期键等操作，所以现有缓存、排行榜、限流和面试上下文能够运行。

但是 Redis 向量检索依赖 Redis Search/Query 与 RedisJSON 等能力。`MODULE LIST` 为空，说明当前实例没有这些能力，不能直接用于 `RedisVectorStore`。

如果现在直接添加 VectorStore 代码，常见结果是：

- 索引创建失败；
- `FT.SEARCH` 是未知命令；
- Spring Boot 启动时 Redis VectorStore 初始化失败；
- 普通 Redis 能 `PING`，但向量测试仍然报错。

因此本任务第一步不是写 Controller，而是准备 Redis Stack。

---

## 三、本任务完成后的最小架构

```text
测试中的三段 Java 技术文本
        ↓
Spring AI Document
        ↓
EmbeddingModel
        ↓
远程 Embedding API 生成 1024 维向量
        ↓
Spring AI VectorStore
        ↓
Redis Stack 保存文本、metadata、向量并建立 HNSW 索引
        ↓
输入“线程池满了以后任务怎么处理”
        ↓
VectorStore 做相似度检索
        ↓
断言“拒绝策略”文本排名第一
```

注意：这里还没有调用 DeepSeek 生成回答，所以严格来说完成的是 RAG 中的 Retrieval（检索）基础，而不是完整的 Retrieval-Augmented Generation。

---

## 四、涉及文件清单

| 类型 | 文件 | 作用 |
|---|---|---|
| 新建 | `docker-compose.rag.yml` | 单独启动支持向量检索的 Redis Stack |
| 修改 | `pom.xml` | 引入 Redis VectorStore starter |
| 修改 | `src/main/resources/application.yml` | 增加非敏感 RAG 配置 |
| 修改 | `src/main/resources/application-local.yml` | 增加本地 Embedding API Key/URL，不提交 Git |
| 新建 | `src/main/java/com/zhimian/config/RagProperties.java` | 承载可调整的 RAG 参数 |
| 新建 | `src/test/java/com/zhimian/rag/VectorStoreIntegrationTest.java` | 跑通真实语义检索闭环 |
| 修改 | `.gitignore` | 确保本地密钥配置和测试输出不入库 |

本任务不会新建 Controller、Service、Mapper 或前端页面。

---

## 五、步骤 0：启动 Redis Stack

### 5.1 为什么使用 Docker

你当前使用的是旧版 Windows Redis。Windows 原生 Redis 与 Redis Stack 模块组合容易产生版本和模块加载问题，而当前电脑已经安装 Docker，因此使用容器是最稳定、最容易复现的方式。

### 5.2 为什么使用 6380 端口

当前普通 Redis 已占用 `6379`。为了不立即破坏现有项目运行状态，第一轮实验让 Redis Stack 使用宿主机 `6380`：

```text
Windows 普通 Redis：localhost:6379
Redis Stack：localhost:6380
```

第一轮测试成功后，再决定是否把整个项目切换到 Redis Stack。不要一开始删除旧 Redis。

### 5.3 新建 `docker-compose.rag.yml`

在项目根目录创建：

```yaml
services:
  rag-redis:
    image: redis/redis-stack-server:latest
    container_name: zhimian-rag-redis
    restart: unless-stopped
    ports:
      - "6380:6379"
    volumes:
      - zhimian-rag-redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  zhimian-rag-redis-data:
```

这里学习四个知识点：

- 容器内 Redis 仍监听 `6379`；
- `6380:6379` 表示把宿主机 6380 映射到容器 6379；
- named volume 保存数据，重启容器不会立刻丢失；
- healthcheck 只验证服务存活，不等于验证向量检索一定正确。

### 5.4 启动与检查

```powershell
docker compose -f docker-compose.rag.yml up -d
docker compose -f docker-compose.rag.yml ps
redis-cli -p 6380 PING
redis-cli -p 6380 MODULE LIST
```

期望：

```text
PONG
```

`MODULE LIST` 中应出现 search/query 相关模块，而不是空结果。

继续验证：

```powershell
redis-cli -p 6380 FT._LIST
```

刚开始没有索引时可以返回空数组，但不应该返回：

```text
ERR unknown command 'FT._LIST'
```

### 5.5 如果 6380 也被占用

查看端口：

```powershell
Get-NetTCPConnection -LocalPort 6380 -ErrorAction SilentlyContinue
```

如果被占用，将 compose 改成 `6381:6379`，后面的 RAG Redis 端口也统一改成 `6381`。

---

## 六、步骤 1：准备 Embedding 模型

### 6.1 为什么不能直接把 DeepSeek ChatModel 当 EmbeddingModel

当前 DeepSeek 负责：

```text
多轮对话、追问、回答评价、报告生成
```

Embedding 模型负责：

```text
文本 → 固定维度浮点向量
```

它们是两个不同职责。`ChatClient` 能正常对话，不代表 Spring 容器中已经存在 `EmbeddingModel`。

### 6.2 本项目推荐组合

```text
ChatModel：继续使用 DeepSeek
EmbeddingModel：使用支持 OpenAI Embeddings 协议的中文模型服务
VectorStore：Redis Stack
```

可以使用阿里云百炼的 OpenAI 兼容 Embedding 接口。模型名称不要写在 Java 常量里，放在本地配置中。

第一版建议：

```text
dimensions = 1024
distance = COSINE
algorithm = HNSW
```

文档入库和 query 查询必须使用同一个模型、同一个维度。换模型或维度后，旧向量索引必须重建。

### 6.3 不要把 API Key 写入公共配置

公共 `application.yml` 只放占位符或非敏感默认值。真实 key 放在已经被 Git 忽略的 `application-local.yml` 或环境变量中。

先确认：

```powershell
git check-ignore src/main/resources/application-local.yml
```

如果没有输出，先修复 `.gitignore`，不要继续填写密钥。

---

## 七、步骤 2：修改 Maven 依赖

当前项目已经有：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

因此不需要重复添加 ChatModel starter。

在 `pom.xml` 中新增：

```xml
<!-- RAG：Redis Stack 向量存储 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-redis</artifactId>
</dependency>
```

版本继续由现有 `spring-ai-bom` 管理，不要在单个依赖上再写 `<version>`。

然后运行：

```powershell
mvn dependency:tree -Dincludes=org.springframework.ai
mvn -DskipTests compile
```

要确认：

- Spring AI 相关模块版本一致；
- 没有手动混入 1.1.x 或 2.x；
- 现有 `ChatClient` 仍能装配。

### 7.1 为什么暂时不升级 Spring AI 大版本

当前项目使用 Spring AI `1.0.0`。RAG 开工时先只增加 VectorStore 依赖，不同时升级 Spring AI 大版本。

原因：如果启动失败，我们应该能明确判断是 Redis/Embedding 配置问题，而不是框架升级带来的 API 变化。等 RAG 最小闭环通过后，可以单独创建依赖升级任务。

---

## 八、步骤 3：配置 ChatModel、EmbeddingModel 与向量库

### 8.1 理解配置覆盖关系

Spring AI 允许 ChatModel 和 EmbeddingModel 使用不同的 base URL 与 API Key：

```text
spring.ai.openai.chat.*       → DeepSeek
spring.ai.openai.embedding.*  → Embedding 服务
```

Embedding 专属配置会覆盖公共的 `spring.ai.openai.base-url/api-key`，因此不会把 DeepSeek key 错发给 Embedding 服务。

### 8.2 在 `application.yml` 放非敏感配置

示例：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6380
      # Spring AI 1.0.0 的 RedisVectorStore 自动配置要求使用 Jedis
      client-type: jedis
  ai:
    model:
      embedding: openai
    openai:
      embedding:
        # URL 与 API Key 由 application-local.yml 提供
        embeddings-path: /v1/embeddings
        options:
          model: ${RAG_EMBEDDING_MODEL:BAAI/bge-m3}
    vectorstore:
      redis:
        initialize-schema: true
        index-name: zhimian-rag-index
        prefix: "zhimian:rag:embedding:"

zhimian:
  rag:
    enabled: true
    top-k: 5
    similarity-threshold: 0.70
    embedding-dimensions: 1024
```

### 8.3 在 `application-local.yml` 放敏感配置

```yaml
spring:
  ai:
    openai:
      embedding:
        base-url: ${RAG_EMBEDDING_BASE_URL}
        api-key: ${RAG_EMBEDDING_API_KEY}
```

不要照抄一个可能已经变化的服务域名。到供应商控制台确认最终接口地址。

Spring AI 默认会在 `base-url` 后追加：

```text
/v1/embeddings
```

因此要检查最终拼接结果，不能出现：

```text
/v1/v1/embeddings
```

如果供应商给出的 base URL 已经包含 `/v1`，可以把 `embeddings-path` 调整为 `/embeddings`。

### 8.4 Redis 连接使用 6380 的 Redis Stack

当前项目已经选择让本地开发环境整体连接 Redis Stack 的 `6380` 端口。Redis Stack 兼容普通 Redis 命令，因此排行榜、面试上下文和限流仍然可以继续使用，同时增加向量检索能力。

这里必须显式配置 `client-type: jedis`。Spring Boot 默认优先使用 Lettuce，但 Spring AI 1.0.0 的 `RedisVectorStoreAutoConfiguration` 以 `JedisConnectionFactory` 作为生效条件；不指定 Jedis 时，即使依赖和 Redis Stack 都正常，Spring 容器里也不会生成 `VectorStore` Bean。

未来生产环境如果要把业务缓存与向量库分开，再分别创建两个 Redis 连接和独立的 `RedisVectorStore` Bean。学习阶段先共用 Redis Stack，配置更简单。

---

## 九、步骤 4：创建 `RagProperties`

新建：

```text
src/main/java/com/zhimian/config/RagProperties.java
```

参考结构：

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
}
```

这一类不要写业务逻辑，它只是把 YAML 配置转换为类型安全的 Java 对象。

需要能解释：

- 为什么 `topK`、threshold 不能写死在 Service；
- 为什么配置类比到处写 `@Value` 更容易测试；
- 为什么阈值 `0.70` 只是实验起点，不是通用标准答案。

---

## 十、步骤 5：先验证 EmbeddingModel

在接触 Redis 前，先确认 Embedding API 能正常工作。

可以在集成测试中注入：

```java
private final EmbeddingModel embeddingModel;
```

核心测试思路：

```java
float[] vector = embeddingModel.embed("线程池有哪些拒绝策略？");

assertThat(vector).isNotNull();
assertThat(vector.length).isEqualTo(1024);
```

如果这里失败，不要检查 Redis，因为请求还没有走到向量库。

常见错误：

| 错误 | 优先检查 |
|---|---|
| 401 | API Key、地域、环境变量是否生效 |
| 404 | base URL 与 embeddings-path 是否重复或缺失 `/v1` |
| model not found | 模型名称、地域、账号权限 |
| 维度不是 1024 | 模型是否支持 dimensions 参数，配置是否生效 |
| Spring 容器没有 `EmbeddingModel` | `spring.ai.model.embedding` 与 starter 配置 |

---

## 十一、步骤 6：编写 VectorStore 集成测试

### 11.1 为什么这是集成测试

该测试真实依赖：

- Embedding 网络 API；
- Redis Stack；
- Spring AI RedisVectorStore；
- 实际相似度排序。

它不能替代为 Mock，否则只能证明“你让 Mock 返回了预期值”。

### 11.2 测试数据设计

准备三段语义明显不同的文本：

```text
A：ThreadPoolExecutor 构造参数包括核心线程数、最大线程数、存活时间、工作队列、线程工厂和拒绝策略。

B：当线程池和任务队列都已达到容量上限时，会执行 RejectedExecutionHandler，JDK 提供 AbortPolicy、CallerRunsPolicy、DiscardPolicy 和 DiscardOldestPolicy。

C：InnoDB 聚簇索引的叶子节点保存完整行数据，二级索引叶子节点保存主键值。
```

查询：

```text
Java 线程池已经满了，后续提交的任务应该怎么处理？
```

期望 B 排名第一。注意查询和文本 B 没有完全相同，但语义接近，这正是向量检索与 `LIKE '%关键字%'` 的区别。

### 11.3 测试类结构

新建：

```text
src/test/java/com/zhimian/rag/VectorStoreIntegrationTest.java
```

建议先使用显式标记：

```java
@Tag("rag-integration")
```

这样普通 `mvn test` 不需要每次消耗 Embedding API，RAG 集成测试可以单独运行。

核心测试代码可以按下面结构手敲：

```java
package com.zhimian.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import redis.clients.jedis.JedisPooled;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("rag-integration")
class VectorStoreIntegrationTest {

    private static final String PREFIX = "zhimian:rag:test:";
    private static final String INDEX = "zhimian-rag-test-index";

    private final List<String> documentIds = List.of(
            "rag-test-thread-pool-params",
            "rag-test-rejection-policy",
            "rag-test-mysql-index"
    );

    private final JedisPooled jedis = new JedisPooled("localhost", 6380);
    private final VectorStore vectorStore;

    VectorStoreIntegrationTest(EmbeddingModel embeddingModel) {
        this.vectorStore = RedisVectorStore.builder(jedis, embeddingModel)
                .indexName(INDEX)
                .prefix(PREFIX)
                .initializeSchema(true)
                .build();
    }

    @Test
    void shouldRetrieveRejectionPolicyForSemanticallySimilarQuery() {
        List<Document> documents = List.of(
                document(documentIds.get(0),
                        "ThreadPoolExecutor 构造参数包括核心线程数、最大线程数、存活时间、工作队列、线程工厂和拒绝策略。",
                        "thread-pool-params"),
                document(documentIds.get(1),
                        "当线程池和任务队列都达到容量上限时，会执行 RejectedExecutionHandler，包括 AbortPolicy、CallerRunsPolicy、DiscardPolicy 和 DiscardOldestPolicy。",
                        "rejection-policy"),
                document(documentIds.get(2),
                        "InnoDB 聚簇索引的叶子节点保存完整行数据，二级索引叶子节点保存主键值。",
                        "mysql-index")
        );

        vectorStore.add(documents);

        SearchRequest request = SearchRequest.builder()
                .query("Java 线程池已经满了，后续提交的任务应该怎么处理？")
                .topK(3)
                .similarityThreshold(0.0)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMetadata().get("topic"))
                .isEqualTo("rejection-policy");

        results.forEach(result -> System.out.printf(
                "score=%s, topic=%s, text=%s%n",
                result.getScore(),
                result.getMetadata().get("topic"),
                result.getText()
        ));
    }

    @AfterEach
    void cleanUp() {
        vectorStore.delete(documentIds);
        jedis.close();
    }

    private Document document(String id, String text, String topic) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(Map.of(
                        "topic", topic,
                        "testData", true
                ))
                .build();
    }
}
```

### 11.4 重要：构造方式要适配 Spring 测试上下文

上面的代码展示核心关系，但测试类还必须让 Spring 创建并注入 `EmbeddingModel`。你可以选择：

1. `@SpringBootTest` + 构造器注入；
2. `@SpringBootTest` + `@Autowired` 字段注入；
3. 写一个只加载 Embedding 配置的最小测试上下文。

对初学者，第一版使用：

```java
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Tag("rag-integration")
class VectorStoreIntegrationTest {
    // 保留上面的构造器注入
}
```

如果完整应用上下文因 MySQL 等外部组件未启动而失败，先启动智面原本需要的 MySQL 和 Redis。不要为了绕过问题把所有依赖都 Mock。

### 11.5 版本 API 差异

项目当前使用 Spring AI 1.0.0。若 IDE 提示 `SearchRequest.builder()` 不存在，先查看当前依赖实际版本的 API；早期版本使用过：

```java
SearchRequest.builder()
```

或：

```java
SearchRequest.defaults()
        .withQuery("...")
        .withTopK(3)
        .withSimilarityThreshold(0.0);
```

不要同时混用两套写法。先以 `mvn dependency:tree` 确认实际版本，再采用该版本 API。

---

## 十二、步骤 7：运行测试

### 12.1 设置本次 PowerShell 会话环境变量

```powershell
$env:RAG_EMBEDDING_BASE_URL="供应商兼容接口的基础地址"
$env:RAG_EMBEDDING_API_KEY="你的Key"
$env:RAG_EMBEDDING_MODEL="BAAI/bge-m3"
$env:RAG_EMBEDDING_DIMENSIONS="1024"
```

环境变量只对当前终端和它启动的子进程生效。重新打开终端后需要重新设置，除非你已经把非公开配置放在 `application-local.yml`。

### 12.2 单独运行 RAG 测试

```powershell
mvn -Dtest=VectorStoreIntegrationTest test
```

成功标准不是只有 `BUILD SUCCESS`，还包括：

- 返回至少一个结果；
- `rejection-policy` 排名第一；
- 打印出的 score 从高到低符合直觉；
- 测试结束后测试 Document 被删除；
- 再运行一次仍然成功，不会因重复 ID 产生脏数据。

### 12.3 再运行全部原有测试

```powershell
mvn test
```

要确认新增 Embedding/VectorStore 自动配置没有破坏原有 49 个测试。

如果普通单元测试因为没有 API Key 而启动 Embedding Bean，说明配置隔离做得不够好。解决方向是：

- RAG Bean 使用 `@ConditionalOnProperty(name = "zhimian.rag.enabled", havingValue = "true")`；
- 测试环境默认 `zhimian.rag.enabled=false`；
- 只有 `VectorStoreIntegrationTest` 显式启用。

不要把假 API Key 提交到测试资源中。

---

## 十三、为什么第一轮检索把 threshold 设为 0

测试中的：

```java
.similarityThreshold(0.0)
```

不是生产推荐值。它只是为了观察三个候选结果的完整分数分布。

正确调试顺序：

1. 先不设门槛，查看相关与不相关文本分别得到多少分；
2. 增加十几组中文技术问题；
3. 统计相关片段最低分和无关片段最高分；
4. 再选择一个初始 threshold；
5. 后续通过正式评估集继续调整。

直接复制 `0.7` 可能恰好有效，也可能让所有结果都被过滤，或者放入大量无关结果。

---

## 十四、常见错误排查顺序

### 14.1 `FT.SEARCH` unknown command

原因：连到了普通 Redis 6379，而不是 Redis Stack 6380。

检查：

```powershell
redis-cli -p 6380 MODULE LIST
redis-cli -p 6380 FT._LIST
```

### 14.2 Connection refused

检查：

```powershell
docker compose -f docker-compose.rag.yml ps
docker logs zhimian-rag-redis
```

### 14.3 401/403

这是 Embedding 服务认证问题，不是 Redis 问题。检查 API Key、账号地域和模型权限。

### 14.4 404

优先检查最终 URL 是否错误：

```text
base-url + embeddings-path
```

尤其注意重复 `/v1`。

### 14.5 向量维度不一致

表现为索引写入失败或检索异常。

处理：

1. 确认模型实际返回维度；
2. 确认配置中的 dimensions；
3. 删除测试索引与测试数据；
4. 使用统一维度重新创建。

不要往已经按 1024 维创建的索引中写入 1536 维向量。

### 14.6 所有结果的排序都不符合直觉

依次排查：

1. 文档和 query 是否使用同一 Embedding 模型；
2. 中文与代码术语是否在该模型支持范围内；
3. 文本是否过短或信息不足；
4. 是否错误连接到了旧索引；
5. 是否只比较一次偶然结果。

不要立刻通过提高 TopK 掩盖检索质量问题。

### 14.7 测试结束仍然留下数据

原因可能是断言前抛异常、ID 不一致或删除 API 使用错误。

解决：

- 使用固定前缀和固定测试 ID；
- 在 `@AfterEach` 清理；
- 必要时使用 Redis key 前缀手工检查；
- 不要执行无前缀的 `FLUSHALL`，它可能删除其他业务数据。

---

## 十五、本任务必须掌握的知识

完成后你应该能不看文档解释：

### 15.1 Embedding 与 ChatModel 的区别

```text
EmbeddingModel：把文本编码成向量，用于检索、聚类、推荐。
ChatModel：根据上下文生成文字，用于问答、评价和报告。
```

### 15.2 向量检索与 MySQL LIKE 的区别

```text
LIKE：匹配字符是否出现。
向量检索：比较语义是否接近。
```

“线程池满了怎么办”不一定包含“RejectedExecutionHandler”，但向量检索仍可能把拒绝策略片段排在第一。

### 15.3 TopK 与 threshold

```text
TopK：最多取多少条。
threshold：最低要多相似才有资格返回。
```

### 15.4 为什么要使用固定 ID

固定 Document ID 让测试重复运行时可以覆盖或识别相同数据，并能精确删除。真实知识库中通常使用 `chunkId` 转成字符串作为 VectorStore Document ID。

### 15.5 为什么 metadata 很重要

向量只解决“像不像”，metadata 解决“它是谁、来自哪里、是否允许被当前查询使用”。

未来会保存：

```text
docId
chunkId
docType
direction
company
categoryId
status/version
```

---

## 十六、完成标准

完成后逐项确认：

- [ ] 当前基础版本已经形成独立 Git commit。
- [ ] `docker-compose.rag.yml` 可以启动 Redis Stack。
- [ ] 普通 Redis 6379 与 Redis Stack 6380 的区别能够说清楚。
- [ ] `redis-cli -p 6380 PING` 返回 `PONG`。
- [ ] `redis-cli -p 6380 MODULE LIST` 不是空结果。
- [ ] `FT._LIST` 不是 unknown command。
- [ ] `pom.xml` 已添加 Redis VectorStore starter，版本仍由 BOM 管理。
- [ ] Embedding API Key 没有写进 Git 跟踪文件。
- [ ] 能单独调用 `EmbeddingModel` 并验证向量维度。
- [ ] 能写入三条测试 Document。
- [ ] 语义查询能把拒绝策略片段排在第一。
- [ ] 能看到每条结果的 score 和 metadata。
- [ ] 测试数据能够被清理，测试可以重复执行。
- [ ] 原有 49 个后端测试仍然通过。
- [ ] 能解释为什么当前只完成了 Retrieval，而不是完整 RAG。

只有这些全部通过，才进入下一任务：

```text
6.6.2 文档表 + Markdown 切片 + 异步向量化入库
```

---

## 十七、推荐手敲顺序

```text
1. 创建当前基础版本 Git commit
        ↓
2. 启动 Redis Stack 6380
        ↓
3. 用 redis-cli 验证 Search 模块
        ↓
4. 添加 Maven 依赖并编译
        ↓
5. 配置 Embedding URL、Key、模型和维度
        ↓
6. 单独验证 EmbeddingModel
        ↓
7. 写入三条 Document
        ↓
8. 执行 similaritySearch
        ↓
9. 验证排序和 score
        ↓
10. 清理测试数据
        ↓
11. 运行原有全部测试
```

每一步只解决一种问题。Embedding 调不通时不要继续写 VectorStore；Redis 模块没有验证时不要开始排查相似度。

---

## 十八、下一阶段预告

完成本任务后，`6.6.2` 才开始真正的知识库工程：

1. 创建 `knowledge_doc` 和 `knowledge_chunk`；
2. 管理员上传 Markdown/TXT；
3. content hash 防重复；
4. 按标题、段落和 token 切分；
5. MySQL 保存原文；
6. VectorStore 保存向量；
7. 文档状态 `PROCESSING/COMPLETED/FAILED`；
8. 失败重试和补偿清理；
9. 检索测试接口；
10. 最后接入错题讲解。

不要提前把 RAG 塞进 `InterviewServiceImpl`。只有“检索结果能稳定命中正确资料”之后，才有资格让它影响面试问题和评分。

---

## 十九、参考资料

- `阶段部署/RAG学习/01-RAG核心概念与前置知识.md`
- `阶段部署/RAG学习/02-RAG在智面项目中的定位与学习路线.md`
- Spring AI Redis Vector Store：`https://docs.spring.io/spring-ai/reference/api/vectordbs/redis.html`
- Spring AI OpenAI Embeddings：`https://docs.spring.io/spring-ai/reference/1.0/api/embeddings/openai-embeddings.html`
- Spring AI Vector Databases：`https://docs.spring.io/spring-ai/reference/api/vectordbs.html`
- 阿里云百炼 OpenAI 兼容 Embedding：`https://help.aliyun.com/zh/model-studio/embedding-interfaces-compatible-with-openai/`

官方文档用于确认当前版本 API 和供应商地址；本文件用于决定在智面项目里按什么顺序实现。
