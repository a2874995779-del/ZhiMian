# 智面 RAG 系统学习与项目实战总目录

> 项目路径：`E:\java-code\ZhiMian`
>
> 本目录是智面项目后续所有 RAG 学习文档的唯一存放位置。以后新增的 RAG 原理、代码实现、测试、调优和部署文档都放在这里，不再分散到 `docs` 或 `阶段部署/阶段代码`。
>
> 学习方式：你按照文档亲手编写代码，我负责 code review、解释错误、补充测试和把关阶段验收。

---

## 一、你现在从哪里开始

按下面顺序阅读和实践，不要跳着写：

1. [01-RAG核心概念与前置知识.md](./01-RAG核心概念与前置知识.md)
2. [02-RAG在智面项目中的定位与学习路线.md](./02-RAG在智面项目中的定位与学习路线.md)
3. [03-RAG向量检索最小闭环与阶段门禁.md](./03-RAG向量检索最小闭环与阶段门禁.md)
4. [04-知识库表与数据模型.md](./04-知识库表与数据模型.md)
5. [05-Markdown清洗与智能切片.md](./05-Markdown清洗与智能切片.md)

前两篇建立认知，第三篇完成向量检索实验，第四篇建立可管理的知识库，第五篇完成文档进入向量化前的清洗与切片。

05 学习过程中如果对 Java 文本 API 不熟悉，先阅读补充专题：

- [专题01-Java字符串、字符编码与文本处理.md](./专题01-Java字符串、字符编码与文本处理.md)

你当前的实际起点是：

```text
先读 01，理解 Embedding、Chunk、向量检索、TopK、threshold
        ↓
再读 02，理解 RAG 在智面里为什么先服务错题讲解
        ↓
开始 03，启动 Redis Stack 并跑通 VectorStoreIntegrationTest
```

不要一上来写知识库 Controller，也不要直接修改 `InterviewServiceImpl`。

---

## 二、整个 RAG 阶段最终要做成什么

完成全部课程后，智面应该形成下面的能力：

```text
管理员上传 Markdown/TXT 技术资料
        ↓
系统清洗并切分文档
        ↓
EmbeddingModel 将每个 chunk 转成向量
        ↓
MySQL 保存文档、chunk 原文和处理状态
        ↓
Redis Stack 保存向量和检索元数据
        ↓
用户答错题目或请求讲解
        ↓
系统检索最相关的知识片段
        ↓
DeepSeek 根据片段生成讲解
        ↓
前端展示答案、引用来源与降级状态
```

完成基础链路后，再扩展：

- RAG 辅助生成面试追问；
- RAG 为回答评价提供知识依据；
- 美团风格、腾讯风格等场景面试使用不同知识包；
- 面试报告根据薄弱点推荐知识资料；
- 对检索质量进行量化评估和持续调优。

---

## 三、课程地图

下面是后续文档的固定编号与内容规划。标记为“已创建”的文档现在可以阅读，其他文档会在你完成前一关并通过 review 后创建。

| 编号 | 文档/任务 | 主要知识 | 项目产出 | 状态 |
|---|---|---|---|---|
| 01 | RAG 核心概念与前置知识 | RAG、Embedding、Chunk、向量、HNSW | 能讲清完整原理 | 已创建 |
| 02 | RAG 在智面中的定位与学习路线 | 业务定位、用户体验、系统边界 | 确定实现顺序 | 已创建 |
| 03 | 向量检索最小闭环 | Redis Stack、EmbeddingModel、VectorStore | 第一个真实语义检索测试 | 已完成 |
| 04 | 知识库表与数据模型 | 文档状态机、MySQL/向量库分工 | `knowledge_document/chunk` 与 Mapper | 已完成 |
| 05 | Markdown 清洗与智能切片 | 标题切分、token 切分、overlap | `MarkdownChunker` 与单元测试 | 已完成 |
| 06 | 异步导入与向量化 | 线程池、批处理、失败重试、补偿 | 文档上传和导入任务 | 下一任务，待创建 |
| 07 | 检索服务与效果评估 | TopK、阈值、去重、Hit@K、MRR | 检索 API 与评估集 | 待创建 |
| 08 | 错题知识库讲解 | 增强 Prompt、引用、降级 | 第一条完整 RAG 业务链路 | 待创建 |
| 09 | RAG 前端交互 | 上传进度、来源展示、错误状态 | 管理端和用户端页面 | 待创建 |
| 10 | 面试追问与评价增强 | 查询改写、metadata filter、超时降级 | RAG 接入 AI 面试 | 待创建 |
| 11 | 安全、监控与性能 | Prompt Injection、指标、缓存、成本 | 可观测和安全边界 | 待创建 |
| 12 | Docker 化与部署 | MySQL、Redis Stack、前后端、环境变量 | 可供同学访问的部署版本 | 待创建 |

这个顺序不能反过来。特别是：

```text
检索准确
    先于
模型回答好听
    先于
接入面试主链路
```

如果检索本身不准确，模型只会把错误资料表达得更自然。

---

## 四、每个阶段统一的学习方法

每一份任务文档都按下面的循环完成。

### 4.1 第一步：先理解输入和输出

写代码前必须回答：

- 这个模块接收什么？
- 它返回什么？
- 数据的事实来源在哪里？
- 失败时谁负责恢复？
- 它会不会阻塞用户请求？

例如向量检索模块：

```text
输入：用户的自然语言问题
输出：按相似度排序的知识片段
事实来源：MySQL 中的文档/chunk
派生索引：Redis Stack 中的向量
失败策略：返回空结果，不能拖垮题库和面试
```

### 4.2 第二步：只写最小可验证代码

不要同时创建十几个类。一次只验证一个未知点：

```text
Embedding API 能否调用
        ↓
向量维度是否正确
        ↓
Redis Stack 能否保存 Document
        ↓
语义查询能否返回正确排序
```

### 4.3 第三步：先测试，再接业务

RAG 有两个容易混淆的错误来源：

- Retrieval 错了：检索到了不相关资料；
- Generation 错了：资料正确，但模型理解或生成错了。

所以必须先测试纯检索结果，不能只看最终 AI 回答。

### 4.4 第四步：提交 code review

每完成一个编号任务，把下面信息一起发给我：

```text
1. 完成的是哪一篇文档
2. 新建和修改了哪些文件
3. 执行了哪些测试
4. 测试输出或具体报错
5. 哪一段原理还不理解
```

我会从以下角度审查：

- 代码是否符合当前项目风格；
- 业务边界是否清楚；
- MySQL 与向量库是否可能不一致；
- 异步任务是否会丢失或重复执行；
- API Key 是否可能泄露；
- 测试是否真的使用了真实组件；
- RAG 效果是否有数据证明。

### 4.5 第五步：通过验收后再创建下一篇

没有通过当前阶段时，不靠增加更多功能掩盖问题。

例如 03 阶段的语义排序不正确，就先排查模型、维度和测试数据，不提前写文档上传功能。

---

## 五、当前项目与 RAG 的技术对应关系

你已经写过的技术并不会作废，它们会在 RAG 中换一个场景继续使用。

| 当前项目能力 | RAG 中的对应使用 |
|---|---|
| Spring Controller/Service/Mapper | 知识库上传、查询和删除接口 |
| MyBatis | 保存文档、chunk 与处理状态 |
| MySQL 事务 | 保证单库内部数据修改一致 |
| Redis | 保存向量索引、任务锁或短期缓存 |
| Redis Lua | 需要原子状态判断时使用，不直接负责语义检索 |
| ThreadPoolExecutor | 异步解析和向量化文档 |
| Spring AI ChatClient | 根据检索资料生成最终答案 |
| SSE | 后续流式输出 RAG 讲解或面试回答 |
| DTO/VO/Entity | 隔离上传参数、数据库实体和前端响应 |
| 状态机 | `PROCESSING → COMPLETED/FAILED` |
| 降级 | 向量库失败时返回题库原答案 |
| 自动化测试 | 验证切片、检索排序、状态流转和补偿 |

真正新增的核心知识只有：

- Embedding；
- 向量相似度；
- 向量数据库；
- Chunk 切分；
- 检索质量评估。

其余部分仍是后端工程能力。

---

## 六、RAG 在代码中的推荐边界

后续代码建议集中在：

```text
src/main/java/com/zhimian/rag
├── controller
├── service
├── mapper
├── model
├── task
└── support
```

推荐职责：

```text
KnowledgeDocumentService
    管上传、文档列表、状态、删除和重试

KnowledgeIngestionService
    管清洗、切片、Embedding 和入库

KnowledgeRetrievalService
    管 query、TopK、threshold、过滤和去重

RagAnswerService
    管增强 Prompt、ChatModel、引用和降级
```

不要让 `InterviewServiceImpl` 直接负责：

- 读取文件；
- 文档切片；
- 连接向量库；
- 创建向量索引；
- 清理文档向量。

面试模块以后只调用一个稳定接口：

```java
List<RetrievedChunk> search(RetrievalQuery query);
```

这样 RAG 出错时可以降级，向量库更换时也不会重写面试业务。

---

## 七、学习期间的 Git 规则

当前 RAG 开工前基线已经提交：

```text
32013b6 feat: complete interview experience and prepare RAG stage
```

从现在开始，每个 RAG 任务单独提交：

```text
chore(rag): add redis stack and embedding configuration
test(rag): add vector store integration test
feat(rag): add knowledge document persistence
feat(rag): add markdown chunking pipeline
feat(rag): add asynchronous ingestion
feat(rag): add retrieval evaluation
feat(rag): add grounded wrong-question explanation
```

每次提交前至少执行：

```powershell
mvn test
npm.cmd run build
git diff --check
git status
```

涉及真实 Embedding API 的集成测试可以单独执行，避免每次普通单元测试都产生费用：

```powershell
mvn -Dtest=VectorStoreIntegrationTest test
```

任何 API Key 都不能出现在 Git diff 中。

---

## 八、建议学习节奏

不按天数死卡，但可以参考以下节奏。

### 第一周：理解与最小检索

- 完成 01、02 阅读；
- 完成 03 的 Redis Stack；
- 调通 EmbeddingModel；
- 写出第一个 VectorStore 集成测试；
- 能解释 TopK 和 threshold。

结果：你已经理解 RAG 中最陌生的部分。

### 第二周：知识进入系统

- 建知识库表；
- 编写 Mapper；
- 处理 Markdown/TXT；
- 实现切片；
- 异步生成向量；
- 处理失败和重试。

结果：管理员放入一篇资料后，系统能稳定建立知识索引。

### 第三周：从检索变成 RAG

- 编写检索服务；
- 建 30 条评估集；
- 记录 Hit@K；
- 接入 DeepSeek；
- 返回答案和引用；
- 完成题目/错题讲解。

结果：形成第一条真正可用的 RAG 业务链路。

### 第四周：产品化与面试增强

- 完成管理端和用户端交互；
- 接入面试追问；
- 增加 metadata filter；
- 增加超时降级和监控；
- Docker 化部署；
- 整理项目表达与面试题。

结果：RAG 不只是实验代码，而是智面的一项稳定功能。

---

## 九、整个阶段的最终验收

### 9.1 功能验收

- [ ] 管理员能够上传 Markdown/TXT。
- [ ] 重复文档不会重复向量化。
- [ ] 文档能显示处理中、成功、失败状态。
- [ ] 文档可以重试和删除。
- [ ] 删除后不能再检索到旧片段。
- [ ] 用户能获取题目的知识库讲解。
- [ ] 回答能显示真实引用来源。
- [ ] 没有命中时能返回题库原答案。
- [ ] 向量库故障不会导致题库或面试不可用。
- [ ] 场景面试能够限定知识范围。

### 9.2 技术验收

- [ ] 文档入库和查询使用相同 Embedding 模型与维度。
- [ ] MySQL 是知识原文和状态的事实来源。
- [ ] 向量索引能够从 MySQL 重建。
- [ ] 异步任务不会读取请求线程的 `UserContext`。
- [ ] MySQL 和向量库失败时有补偿与重试策略。
- [ ] Prompt 明确区分系统指令和外部知识。
- [ ] metadata filter 不相信前端传入的 userId。
- [ ] 检索质量和生成质量分开测试。
- [ ] Embedding API Key 没有进入仓库。

### 9.3 学习验收

你应该能独立解释：

1. RAG 为什么不是微调；
2. EmbeddingModel 与 ChatModel 的区别；
3. 为什么整篇文档不能只生成一个向量；
4. HNSW 为什么比 FLAT 快；
5. TopK 和 threshold 如何影响召回；
6. 为什么 RAG 只能减少幻觉，不能消灭幻觉；
7. MySQL 与向量库如何做到最终一致；
8. 向量库宕机后智面如何降级；
9. 如何证明检索调优有效；
10. RAG 在智面中的真实业务价值。

---

## 十、当前行动清单

现在只做下面这些：

- [x] 完成 03 的 Embedding API 与 Redis Stack 最小闭环。
- [x] 完成 04 的知识文档表、知识片段表、Mapper、状态机与测试。
- [x] 阅读 `05-Markdown清洗与智能切片.md`。
- [x] 理解标题路径、Token 窗口和 overlap。
- [x] 完成 Cleaner、Parser、Splitter 和 Chunker。
- [x] 完成四组不依赖外部服务的单元测试。
- [x] 执行完整 `mvn test`，共 105 个测试通过，5 个外部集成测试按开关跳过。
- [x] 完成 05 Code Review，并修复 Fence、异常类型和 Unicode Token 边界问题。

当前阶段不要做：

- 不写知识库前端；
- 不支持 PDF/Word；
- 不在 05 阶段写 MySQL 和 Redis；
- 不调用 Embedding API；
- 不改面试 Prompt；
- 不做 rerank；
- 不做多租户知识库；
- 不追求复杂 Agent；
- 不凭感觉调整十几个参数。

05 已通过验收。下一步进入 06 前，先理解“异步编排、状态迁移、MySQL 与 Redis 最终一致性”三件事。
