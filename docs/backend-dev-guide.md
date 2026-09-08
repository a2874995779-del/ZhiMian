# 智面(ZhiMian)—— AI 面试刷题助手平台 · 后端开发文档

> 面向求职者的刷题 + AI 模拟面试平台。本文档是整个项目的"施工图纸":架构、库表、接口、任务拆解、面试考点地图。
>
> **协作方式**:业务代码由你(学员)编写,我(导师)负责审查、讲解和面试拷打。每个任务粒度控制在 1~3 小时。

---

## 目录

1. [整体架构说明](#1-整体架构说明)
2. [数据库表设计](#2-数据库表设计)
3. [Redis 数据结构设计](#3-redis-数据结构设计)
4. [接口设计](#4-接口设计)
5. [开发任务清单(按阶段)](#5-开发任务清单)
6. [面试考点地图](#6-面试考点地图)

---

## 1. 整体架构说明

### 1.1 总体形态:单体分层架构

**为什么是单体而不是微服务?**
面试项目的第一原则是"你能讲清楚每一行代码为什么存在"。微服务引入的注册中心、网关、分布式事务等复杂度,对单人学习项目是纯负担。单体做好了,面试时反而可以主动聊"如果要拆微服务,我会怎么拆"(按题库服务 / 面试服务 / 用户服务的边界拆),这比一个跑不起来的伪微服务加分得多。

### 1.2 分层结构

```
┌─────────────────────────────────────────────┐
│  Controller 层(接收请求、参数校验、返回 VO)      │
├─────────────────────────────────────────────┤
│  Service 层(业务逻辑、事务边界、缓存读写)        │
├─────────────────────────────────────────────┤
│  Mapper 层(MyBatis 接口 + XML,只做数据存取)    │
├──────────────┬──────────────┬───────────────┤
│   MySQL 8.0  │    Redis     │  AI 服务(HTTP) │
└──────────────┴──────────────┴───────────────┘
```

分层铁律(违反了我会在 review 时指出来):

- Controller 不写业务逻辑,只做:参数校验 → 调 Service → 组装返回。
- Service 之间可以互相调用,但**不允许 Service 反向依赖 Controller**,也不允许 Mapper 之间互相调用。
- 事务(`@Transactional`)只加在 Service 层。
- Controller 对外返回 **VO/DTO**,不允许把 Entity(数据库实体)直接扔给前端(密码字段泄露就是这么来的)。

### 1.3 三类模型对象的约定

| 类型 | 位置 | 用途 |
|------|------|------|
| Entity | `model/entity` | 与数据库表一一对应,只在 Service/Mapper 层流动 |
| DTO | `model/dto` | 接收前端请求参数(如 `UserRegisterDTO`),带校验注解 |
| VO | `model/vo` | 返回给前端的视图对象(如 `QuestionVO`,不含 answer 的敏感场景) |

### 1.4 模块划分与目录结构树

```
zhimian
├── pom.xml
├── sql/
│   └── init.sql                          # 建库建表脚本
├── docs/
│   └── backend-dev-guide.md              # 本文档
└── src/main
    ├── java/com/zhimian
    │   ├── ZhiMianApplication.java       # 启动类
    │   ├── common/                       # 通用组件(不含业务)
    │   │   ├── Result.java               # 统一返回体
    │   │   ├── PageResult.java           # 分页返回体
    │   │   ├── ErrorCode.java            # 错误码枚举
    │   │   └── UserContext.java          # ThreadLocal 保存当前登录用户
    │   ├── config/                       # 配置类
    │   │   ├── WebConfig.java            # 注册拦截器、CORS
    │   │   ├── RedisConfig.java          # RedisTemplate 序列化配置
    │   │   └── AiConfig.java             # ChatClient 装配(阶段二)
    │   ├── exception/
    │   │   ├── BusinessException.java    # 业务异常
    │   │   └── GlobalExceptionHandler.java
    │   ├── interceptor/
    │   │   └── JwtInterceptor.java       # 登录鉴权拦截器
    │   ├── controller/                   # 按业务域分文件
    │   ├── service/                      # 接口 + impl 子包
    │   ├── mapper/                       # MyBatis 接口
    │   ├── model/
    │   │   ├── entity/  ├── dto/  └── vo/
    │   ├── ai/                           # 阶段二:面试官 prompt、上下文管理、报告解析
    │   └── rag/                          # 阶段三:文档切分、向量化、检索
    └── resources
        ├── application.yml
        ├── application-local.yml         # 本地密钥,不进 git
        └── mapper/                       # MyBatis XML,与 mapper 接口同名
```

**为什么按"技术分层"而不是按"业务模块"分包?** 两种都行。项目规模小于 30 张表时技术分层更直观;面试被问到时,能说出"如果模块继续膨胀,我会切换成 `question/`、`interview/` 这种按域分包,为拆服务做准备"即可。

### 1.5 关键技术决策(及理由)

| 决策 | 选择 | 理由 |
|------|------|------|
| 鉴权框架 | 自写拦截器,**不用 Spring Security** | 你需要练的是 JWT 原理、拦截器执行链、ThreadLocal;Security 的过滤器链会把这些全部黑盒化,面试讲不清。等项目稳定后可以作为"演进方向"聊 |
| JWT 库 | jjwt 0.12.x | 社区主流,API 链式清晰,支持的算法全 |
| 密码存储 | BCrypt(用 `spring-security-crypto` 单独引入,不引入整个 Security) | 自带随机盐、可调 cost factor,面试标准答案;MD5+盐是反面教材 |
| ORM | MyBatis(XML 写 SQL) | 你的明确要求,练动态 SQL |
| AI 接入 | Spring AI 1.0 + DeepSeek(OpenAI 兼容协议) | Spring AI 提供 `ChatClient`、流式 `Flux`、`BeanOutputConverter`(结构化输出)、`VectorStore` 抽象,阶段二/三全用得上。通义千问可作为备选(spring-ai-alibaba) |
| 接口风格 | RESTful + 统一返回体 | 见第 4 节 |

---

## 2. 数据库表设计

### 2.0 全局设计规范(先读这个)

1. **主键**:统一 `BIGINT AUTO_INCREMENT`。面试考点:为什么不用 UUID?——UUID 无序,导致 InnoDB 聚簇索引页分裂、插入性能差、索引占空间大。如果以后分库分表再换雪花算法。
2. **通用字段**:每张表都有 `create_time`、`update_time`(数据库层面用 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP` 自动维护),业务主表加 `is_deleted` 逻辑删除。
3. **逻辑删除**:`is_deleted TINYINT DEFAULT 0`。考点:为什么不物理删除?——数据可恢复、外键引用不悬空;代价是所有查询要带 `is_deleted = 0`,且唯一索引要处理"删除后重名"问题(本项目用户名唯一索引的处理见 user 表说明)。
4. **字符集**:`utf8mb4`。考点:utf8 和 utf8mb4 的区别(MySQL 的 utf8 最多 3 字节,存不了 emoji 和部分生僻字)。
5. **不用外键约束**(FOREIGN KEY):互联网项目惯例,外键在高并发下有锁开销、影响分库分表,一致性由业务代码保证。面试常问,要能讲出这条理由。
6. **索引命名**:普通索引 `idx_字段`,唯一索引 `uk_字段`。

### 2.1 建库

```sql
CREATE DATABASE IF NOT EXISTS zhimian
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 2.2 用户表 user(阶段一)

```sql
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(64)  NOT NULL COMMENT '登录账号',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文(固定 60 字符,留余量)',
  `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
  `role`        VARCHAR(16)  NOT NULL DEFAULT 'user' COMMENT '角色:user/admin',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-正常 1-封禁',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-否 1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';
```

**字段设计理由:**

- `password VARCHAR(100)`:BCrypt 输出固定 60 字符(格式 `$2a$10$...`),给 100 留算法升级余量。**没有 salt 字段**——BCrypt 把盐编码在密文里,这是面试高频追问点("你的盐存哪了?")。
- `role` 用字符串而不是 tinyint:可读性好,角色只有两种,不值得建角色表(RBAC 是过度设计,但要能在面试时说出"如果权限变复杂,演进方向是 user-role-permission 三表 RBAC")。
- **索引设计**:`uk_username` 唯一索引,同时承担两个职责:① 注册防重(靠数据库兜底,而不是只靠代码里先查后插——先查后插在并发下有竞态,考点!);② 登录时按 username 查询走索引。
- 逻辑删除与唯一索引的冲突:用户注销后 username 仍占用唯一索引。本项目接受这个限制(注销即永久占用该名字);要能讲出通用解法(删除时把 username 改写成 `username_del_{id}`)。

### 2.3 分类表 category(阶段一)

```sql
CREATE TABLE `category` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64) NOT NULL COMMENT '分类名,如 Java并发、MySQL',
  `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类 id,0 表示顶级',
  `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序值,越小越靠前',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '题目分类表';
```

- `parent_id` 邻接表方案支持两级分类(如 "Java > 并发")。分类总量小(几十条),树的组装在内存里做,不需要递归 SQL。
- 分类数据量小、几乎只读 → 是**整表缓存**的理想对象(阶段一任务 1.9 会用到)。

### 2.4 题目表 question(阶段一)

```sql
CREATE TABLE `question` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `title`          VARCHAR(256) NOT NULL COMMENT '题目标题',
  `content`        TEXT         DEFAULT NULL COMMENT '题干补充描述(Markdown)',
  `answer`         TEXT         NOT NULL COMMENT '参考答案(Markdown)',
  `difficulty`     TINYINT      NOT NULL DEFAULT 1 COMMENT '难度 1-简单 2-中等 3-困难',
  `category_id`    BIGINT       NOT NULL COMMENT '分类 id',
  `view_count`     INT          NOT NULL DEFAULT 0 COMMENT '浏览量(定时从 Redis 回写)',
  `create_user_id` BIGINT       NOT NULL COMMENT '创建人 id',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '题目表';
```

**索引设计(重点,面试必问):**

- `idx_category_id`:题库最高频的查询是"按分类分页",没有它就是全表扫描。
- `idx_create_time`:支持"最新题目"排序分页。思考题(我会拷打你):列表页 SQL 是 `WHERE category_id = ? AND is_deleted = 0 ORDER BY create_time DESC LIMIT x,y`,更优的索引是什么?——联合索引 `(category_id, create_time)`,既过滤又排序,避免 filesort。**任务 1.7 里你要用 EXPLAIN 自己验证这个结论,然后决定是否改索引。**
- `is_deleted` 为什么不建索引?——区分度极低(绝大多数是 0),B+ 树索引对低区分度字段收益接近零,考点:索引选择性。
- 标题搜索用 `LIKE '%xx%'` 起步(无法走索引,数据量小可接受),面试演进话术:数据量大后上 ES 或 MySQL 全文索引。

### 2.5 标签表 tag + 关联表 question_tag(阶段一)

```sql
CREATE TABLE `tag` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(32) NOT NULL COMMENT '标签名,如 JVM、线程池',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '标签表';

CREATE TABLE `question_tag` (
  `question_id` BIGINT NOT NULL,
  `tag_id`      BIGINT NOT NULL,
  PRIMARY KEY (`question_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '题目-标签关联表';
```

- 多对多标准三表方案。联合主键 `(question_id, tag_id)` 天然防重,且支持"查某题的所有标签"(走主键左前缀)。
- `idx_tag_id` 反向索引支持"按标签查题目"。考点:联合主键的最左前缀原则——只按 `tag_id` 查时主键索引用不上,所以需要这个二级索引。

### 2.6 答题记录表 answer_record(阶段一)

```sql
CREATE TABLE `answer_record` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL COMMENT '答题用户',
  `question_id` BIGINT   NOT NULL COMMENT '题目',
  `result`      TINYINT  NOT NULL COMMENT '0-答错 1-答对(用户自评)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_question_id` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '答题记录表';
```

- 纯流水表,只插不改,不需要 `update_time`/`is_deleted`。
- `idx_user_time` 联合索引:支撑"我的答题历史"按时间倒序分页。
- 排行榜**不查这张表**,读走 Redis ZSet(见第 3 节);这张表是持久化事实来源,Redis 丢了可以从这里重建——考点:缓存与数据库谁是 source of truth。

### 2.7 面试会话表 interview_session(阶段二)

```sql
CREATE TABLE `interview_session` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT      NOT NULL COMMENT '所属用户',
  `direction`   VARCHAR(32) NOT NULL COMMENT '面试方向:java_concurrency/mysql/redis...',
  `title`       VARCHAR(128) DEFAULT NULL COMMENT '会话标题(默认取方向+日期)',
  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0-进行中 1-已结束 2-报告已生成',
  `end_time`    DATETIME    DEFAULT NULL COMMENT '结束时间',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 面试会话表';
```

- `status` 状态机:0 → 1(用户点结束)→ 2(报告生成完成)。状态流转只能单向,Service 层校验。
- `direction` 用编码字符串而不是关联分类表:面试方向和题库分类是两套语义,耦合起来以后会互相牵制。

### 2.8 面试消息表 interview_message(阶段二)

```sql
CREATE TABLE `interview_message` (
  `id`          BIGINT     NOT NULL AUTO_INCREMENT,
  `session_id`  BIGINT     NOT NULL COMMENT '会话 id',
  `role`        VARCHAR(16) NOT NULL COMMENT 'user/assistant/system',
  `content`     MEDIUMTEXT NOT NULL COMMENT '消息内容',
  `tokens`      INT        DEFAULT NULL COMMENT '该消息估算 token 数',
  `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '面试消息表';
```

- **双写架构**:热数据(当前对话上下文)在 Redis,MySQL 存全量历史。为什么不只存 Redis?——Redis 是内存库,重启/淘汰会丢;历史记录是低频读,放 MySQL。为什么不只存 MySQL?——每轮对话都要读最近 N 条拼 prompt,高频读走 Redis。这个设计本身就是一道面试题。
- `MEDIUMTEXT`:AI 单条回答可能超过 TEXT 在行内的舒适范围,MEDIUMTEXT 上限 16MB 足够。
- 查询模式固定为"按 session_id 拉全量按 id 排序",`idx_session_id` + 主键排序即可。

### 2.9 面试报告表 interview_report(阶段二)

```sql
CREATE TABLE `interview_report` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `session_id`  BIGINT   NOT NULL COMMENT '会话 id',
  `score`       INT      NOT NULL COMMENT '总分 0-100',
  `content`     JSON     NOT NULL COMMENT '结构化报告:亮点/薄弱点/建议',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '面试评价报告表';
```

- `content` 用 MySQL 8 的 **JSON 类型**:报告结构会随 prompt 迭代变化,JSON 免去频繁加列;`score` 单独拉出来成列,因为它需要被筛选/排序(JSON 内字段查询要用虚拟列+索引,复杂)。考点:什么时候该用 JSON 列、它的代价(无法直接索引、无强 schema)。
- `uk_session_id` 唯一索引保证一个会话只有一份报告,同时也是幂等兜底:重复点"生成报告"不会插两条。

### 2.10 知识库文档表 knowledge_doc + 切片表 knowledge_chunk(阶段三)

```sql
CREATE TABLE `knowledge_doc` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `title`       VARCHAR(128) NOT NULL COMMENT '文档标题',
  `source`      VARCHAR(255) DEFAULT NULL COMMENT '来源(文件名/URL)',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-处理中 1-已完成 2-失败',
  `chunk_count` INT          NOT NULL DEFAULT 0 COMMENT '切片数量',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'RAG 知识库文档表';

CREATE TABLE `knowledge_chunk` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `doc_id`      BIGINT      NOT NULL COMMENT '所属文档',
  `chunk_index` INT         NOT NULL COMMENT '切片在文档中的序号',
  `content`     TEXT        NOT NULL COMMENT '切片原文',
  `vector_id`   VARCHAR(64) DEFAULT NULL COMMENT '向量库中的 id',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'RAG 文档切片表';
```

- 向量本身存向量库,MySQL 存**原文和元数据**。`vector_id` 是两边的关联键——删除文档时需要据此清理向量库,考点:跨存储的数据一致性(先删哪边?失败了怎么补偿?)。
- `status` 支持异步导入:导入接口立即返回,后台线程做切分+向量化,前端轮询状态。

### 2.11 错题本 wrong_question(阶段四)

```sql
CREATE TABLE `wrong_question` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT   NOT NULL,
  `question_id`     BIGINT   NOT NULL,
  `wrong_count`     INT      NOT NULL DEFAULT 1 COMMENT '累计答错次数',
  `status`          TINYINT  NOT NULL DEFAULT 0 COMMENT '0-未掌握 1-已掌握',
  `last_wrong_time` DATETIME NOT NULL COMMENT '最近答错时间',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`, `question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '错题本';
```

- `uk_user_question` 唯一索引 + `INSERT ... ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1`:一条 SQL 完成"没有就插入、有就累加",天然并发安全。这是练"用数据库特性替代先查后写"的好例子,面试可讲。

---

## 3. Redis 数据结构设计

Key 统一前缀 `zhimian:`,冒号分层。**每个 key 都必须有明确的过期策略**(排行榜类除外),这是 review 红线。

| Key 模式 | 类型 | 用途 | 过期 |
|----------|------|------|------|
| `zhimian:cache:question:hot` | String(JSON) | 热门题目列表缓存 | 5 分钟 + 随机抖动(防雪崩) |
| `zhimian:cache:question:detail:{id}` | String(JSON) | 题目详情缓存 | 30 分钟;空值缓存 1 分钟(防穿透) |
| `zhimian:cache:category:all` | String(JSON) | 全量分类缓存 | 1 小时,写操作后主动删除 |
| `zhimian:question:view:{id}` | String(计数) | 浏览量增量,定时任务回写 MySQL | 无(回写后删除) |
| `zhimian:rank:answer:total` | ZSet | 总答题排行榜,member=userId,score=答对数 | 无 |
| `zhimian:rank:answer:{yyyyMMdd}` | ZSet | 日答题排行榜 | 7 天 |
| `zhimian:interview:ctx:{sessionId}` | List(JSON 消息) | 面试对话上下文窗口 | 2 小时,每次对话续期 |
| `zhimian:interview:lock:{sessionId}` | String | 会话级锁:防同一会话并发提问 | 60 秒 |
| `zhimian:jwt:blacklist:{jti}` | String | 登出后的 token 黑名单 | 与 token 剩余有效期一致 |

设计要点(都是考点):

- **缓存更新策略**:统一采用 Cache Aside(先更新 DB,再删缓存)。要能回答"为什么是删缓存不是更新缓存"、"为什么先更 DB 后删缓存"(旁路缓存 + 延迟双删的讨论)。
- **排行榜为什么用 ZSet**:跳表实现,`ZINCRBY` O(logN) 更新、`ZREVRANGE` O(logN+M) 取 TopN,天然去重排序。要会讲跳表 vs 红黑树(范围查询、实现复杂度)。
- **对话上下文为什么用 List**:消息天然有序,`RPUSH` 追加、`LRANGE -N -1` 取最近 N 条做 token 窗口裁剪。

---

## 4. 接口设计

### 4.1 统一返回格式

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

分页数据统一包裹:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "records": [],
    "total": 100,
    "pageNum": 1,
    "pageSize": 10
  }
}
```

**约定:HTTP 状态码恒为 200(SSE 与框架级错误除外),业务成败看 `code`。** 这是国内主流做法;另一派主张业务错误也用 HTTP 4xx/5xx,两派各有道理,面试被问到要能说出:统一 200 便于前端统一拦截、避免网关/代理对 4xx 的特殊处理;代价是监控系统无法直接从状态码看出错误率。

### 4.2 错误码规范

五位数字,前三位对齐 HTTP 语义,后两位细分:

| code | 含义 | 典型场景 |
|------|------|----------|
| 0 | 成功 | |
| 40000 | 请求参数错误 | 校验注解不通过、JSON 解析失败 |
| 40100 | 未登录 | 无 token / token 无效 |
| 40101 | 登录已过期 | token 过期或已登出(黑名单) |
| 40300 | 无权限 | user 调用 admin 接口 |
| 40400 | 资源不存在 | 题目 id 不存在 |
| 40900 | 资源冲突 | 用户名已存在 |
| 42900 | 请求过于频繁 | 限流触发、会话有未完成的提问 |
| 50000 | 系统内部异常 | 未捕获异常兜底 |
| 50001 | AI 服务调用失败 | 模型超时、配额耗尽 |

### 4.3 接口总览

统一前缀 `/api`。🔒 = 需登录,👑 = 需 admin。

#### 认证与用户(阶段一)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录,返回 token |
| POST | `/api/auth/logout` | 🔒 登出(token 进黑名单) |
| GET | `/api/users/me` | 🔒 当前用户信息 |

**注册** `POST /api/auth/register`

```json
// 请求
{ "username": "zhangsan", "password": "abc12345", "nickname": "张三" }
// 响应
{ "code": 0, "message": "ok", "data": 10001 }   // data = 新用户 id
// 失败
{ "code": 40900, "message": "用户名已存在", "data": null }
```

**登录** `POST /api/auth/login`

```json
// 请求
{ "username": "zhangsan", "password": "abc12345" }
// 响应
{
  "code": 0, "message": "ok",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9....",
    "user": { "id": 10001, "username": "zhangsan", "nickname": "张三", "role": "user" }
  }
}
```

之后所有 🔒 接口在请求头携带:`Authorization: Bearer {token}`。

#### 题库(阶段一)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/questions` | 分页查询(条件:categoryId、tagId、difficulty、keyword) |
| GET | `/api/questions/{id}` | 题目详情(浏览量 +1) |
| GET | `/api/questions/hot` | 热门题目 Top10(走缓存) |
| POST | `/api/questions` | 👑 新增题目 |
| PUT | `/api/questions/{id}` | 👑 修改题目 |
| DELETE | `/api/questions/{id}` | 👑 删除题目(逻辑删除) |
| GET | `/api/categories` | 分类树 |
| POST | `/api/categories` | 👑 新增分类 |
| GET | `/api/tags` | 标签列表 |
| POST | `/api/tags` | 👑 新增标签 |

**分页查询** `GET /api/questions?pageNum=1&pageSize=10&categoryId=2&difficulty=2&keyword=线程池`

```json
{
  "code": 0, "message": "ok",
  "data": {
    "records": [
      {
        "id": 1, "title": "线程池的核心参数有哪些?",
        "difficulty": 2, "categoryId": 2, "categoryName": "Java并发",
        "tags": ["线程池"], "viewCount": 320, "createTime": "2026-07-01 10:00:00"
      }
    ],
    "total": 57, "pageNum": 1, "pageSize": 10
  }
}
```

注意:**列表接口不返回 `answer` 字段**(刷题场景先看题后看答案),详情接口才返回。

**新增题目** `POST /api/questions` 👑

```json
// 请求
{
  "title": "线程池的核心参数有哪些?",
  "content": "请结合 ThreadPoolExecutor 构造函数说明。",
  "answer": "corePoolSize、maximumPoolSize、keepAliveTime...",
  "difficulty": 2, "categoryId": 2, "tagIds": [3, 5]
}
// 响应:data = 新题目 id
```

#### 答题与排行榜(阶段一)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/questions/{id}/answer` | 🔒 提交答题结果 `{ "result": 1 }` |
| GET | `/api/ranks/answer?type=total&limit=10` | 排行榜(type=total/daily) |

```json
// 排行榜响应
{
  "code": 0, "message": "ok",
  "data": [
    { "rank": 1, "userId": 10001, "nickname": "张三", "count": 152 },
    { "rank": 2, "userId": 10002, "nickname": "李四", "count": 149 }
  ]
}
```

#### AI 面试(阶段二)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/interviews` | 🔒 创建会话 `{ "direction": "java_concurrency" }`,返回会话 id + AI 开场白 |
| POST | `/api/interviews/{id}/chat` | 🔒 发送回答,**SSE 流式**返回 AI 下一问 |
| POST | `/api/interviews/{id}/finish` | 🔒 结束面试并生成报告 |
| GET | `/api/interviews` | 🔒 我的会话列表(分页) |
| GET | `/api/interviews/{id}` | 🔒 会话详情 + 全部消息 |
| GET | `/api/interviews/{id}/report` | 🔒 查看报告 |

**SSE 对话** `POST /api/interviews/{id}/chat`,请求体 `{ "content": "我认为 volatile 保证可见性..." }`,响应 `Content-Type: text/event-stream`:

```
data: {"type":"delta","content":"嗯,"}

data: {"type":"delta","content":"那么它能保证原子性吗?"}

data: {"type":"done","messageId":88}
```

约定:每个 event 的 data 是 JSON;`type=delta` 为增量文本,`type=done` 标记结束并携带落库后的消息 id,`type=error` 携带错误信息(SSE 已开流,不能再改 HTTP 状态码,错误只能作为事件下发——这是 SSE 的固有约束,考点)。

**报告响应** `GET /api/interviews/{id}/report`

```json
{
  "code": 0, "message": "ok",
  "data": {
    "score": 72,
    "highlights": ["对 volatile 的可见性原理理解到位,能讲到 MESI"],
    "weaknesses": [
      { "topic": "AQS", "detail": "对 CLH 队列的入队流程描述模糊", "suggestion": "重读 AQS 源码 acquire 流程" }
    ],
    "summary": "基础扎实但源码深度不足,建议..."
  }
}
```

#### RAG 知识库(阶段三)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/docs` | 👑 上传文档(multipart),异步处理,返回 docId |
| GET | `/api/knowledge/docs` | 👑 文档列表(含处理状态) |
| DELETE | `/api/knowledge/docs/{id}` | 👑 删除文档(连带清理向量库) |
| GET | `/api/knowledge/search?query=xxx&topK=3` | 🔒 检索调试接口 |
| GET | `/api/questions/{id}/reference` | 🔒 答错后获取 RAG 参考答案与出处 |

```json
// reference 响应
{
  "code": 0, "message": "ok",
  "data": {
    "answer": "根据知识库,线程池拒绝策略有四种...",
    "sources": [
      { "docTitle": "Java并发编程八股", "chunkContent": "……原文片段……", "score": 0.87 }
    ]
  }
}
```

#### 错题本与复习计划(阶段四)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/wrong-questions` | 🔒 我的错题分页 |
| PUT | `/api/wrong-questions/{id}/master` | 🔒 标记已掌握 |
| POST | `/api/study-plans/generate` | 🔒 AI + Function Calling 生成复习计划 |
| GET | `/api/study-plans` | 🔒 我的复习计划列表 |

---

## 5. 开发任务清单

> 每个任务的格式:**思路提示 / 核心知识点 / 验收标准 / 易踩的坑**。
> 记住协作规则:你先写,卡住超过两次提示我才给参考实现。

### 阶段一:基础骨架(预计 10~13 个工作单元)

#### 任务 1.0 环境准备与骨架跑通(导师完成脚手架,你负责跑通)✅

- **内容**:安装 MySQL 8 / Redis,执行 `sql/init.sql`,填好 `application-local.yml`,启动项目访问健康检查接口。
- **验收**:`curl http://localhost:8080/api/health` 返回统一格式的成功响应;控制台无报错。

#### 任务 1.1 用户注册接口(约 2h)

- **思路提示**:DTO 加 `jakarta.validation` 注解(`@NotBlank`、`@Size`)→ Controller 加 `@Valid` → Service 里 BCrypt 加密后插入 → 捕获唯一索引冲突转成 40900 业务异常。MyBatis 写 `insert` 并配置 `useGeneratedKeys` 拿回自增 id。
- **核心知识点**:参数校验体系(`@Valid` 触发时机、`MethodArgumentNotValidException`);BCrypt 原理(为什么慢是优点、盐存在哪);数据库唯一索引兜底并发注册。
- **验收标准**:① 正常注册返回用户 id;② 重复用户名返回 40900;③ 密码在库里是 `$2a$` 开头密文;④ 用两个并发请求注册同名用户,只有一个成功(可用 `ab` 或写个小脚本)。
- **易踩的坑**:先 `SELECT` 判断重名再 `INSERT`,并发下两个请求都通过判断 → 必须靠唯一索引 + 捕获 `DuplicateKeyException` 兜底;校验异常没有被全局异常处理器接住,返回了 Spring 默认错误 JSON。

#### 任务 1.2 登录 + JWT 签发(约 2h)

- **思路提示**:查用户 → `BCrypt.matches` 比对 → jjwt 生成 token(claims 放 userId、role、jti,过期设 24h,密钥从配置读且长度 ≥ 32 字节)→ 返回 token + 用户 VO(**绝不能带 password 字段**)。
- **核心知识点**:JWT 三段结构(header.payload.signature)、签名防篡改原理、对称 HS256 vs 非对称 RS256 的取舍;JWT vs Session 的对比(无状态、无法主动失效的痛点)。
- **验收标准**:① 正确密码拿到 token,去 jwt.io 解码能看到 userId;② 错误密码返回 40100 且**提示语不区分"用户不存在"和"密码错误"**(防用户名枚举);③ 篡改 token 任意一个字符后访问受保护接口被拒。
- **易踩的坑**:把密码哈希或手机号放进 payload(payload 只是 Base64,可解码不加密!);密钥硬编码在代码里;返回的 user 对象直接用了 Entity 带出 password。

#### 任务 1.3 JWT 拦截器 + ThreadLocal 用户上下文(约 2~3h)

- **思路提示**:实现 `HandlerInterceptor`,`preHandle` 里解析 `Authorization` 头 → 验签、验过期、查黑名单 → 把 userId/role 放进 `UserContext`(ThreadLocal)→ `afterCompletion` 里 **remove**。`WebConfig` 注册拦截器并放行 `/api/auth/**`、`/api/health`。admin 校验可以先在拦截器里做简单版(或自定义 `@RequireAdmin` 注解 + 拦截器读注解)。
- **核心知识点**:拦截器 vs 过滤器(归属容器、执行顺序、能否拿到 handler);ThreadLocal 原理与内存泄漏(线程池复用线程,不 remove 会串用户!);拦截器里抛异常能否被 `@RestControllerAdvice` 捕获(能,这是选拦截器不选过滤器的理由之一)。
- **验收标准**:① 不带 token 访问 `/api/users/me` 返回 40100;② 带合法 token 返回当前用户;③ user 角色调用 👑 接口返回 40300;④ 用日志验证同一线程处理第二个请求时 ThreadLocal 已被清理。
- **易踩的坑**:忘记 remove 导致用户串号(Tomcat 线程复用);放行路径写错导致注册接口也要登录;`afterCompletion` 和 `postHandle` 的区别没搞清(异常时 postHandle 不执行)。

#### 任务 1.4 登出与 token 黑名单(约 1h)

- **思路提示**:登出时取 token 的 jti 和剩余有效期,写入 `zhimian:jwt:blacklist:{jti}`,TTL = 剩余有效期;拦截器验签后多查一次黑名单。
- **核心知识点**:JWT 无法主动失效的解决方案对比(黑名单 / 短 token + refresh token / 版本号);Redis TTL 的惰性删除 + 定期删除。
- **验收标准**:登出后原 token 立即失效(返回 40101);黑名单 key 会随 token 过期自动消失(`TTL` 命令验证)。
- **易踩的坑**:TTL 设成固定值而不是 token 剩余时间(要么白占内存要么提前放行);每个请求多一次 Redis 查询,要能回答"这是否违背 JWT 无状态初衷"(是,这是安全性与无状态的权衡)。

#### 任务 1.5 分类与标签管理(约 1.5h)

- **思路提示**:两组简单 CRUD。分类树在 Service 内存组装(一次查全量,按 parentId 分组)。这是你第一次独立写完整的 Controller-Service-Mapper 链路,重点是把规范走顺。
- **核心知识点**:MyBatis `resultMap` 基础;一次查询内存建树 vs 递归查询的取舍。
- **验收标准**:新增/查询正常;分类接口返回两级树结构;重名分类返回 40900。
- **易踩的坑**:建树时用嵌套循环 O(n²)(用 Map 分组 O(n));Mapper XML 的 namespace 和接口全限定名不一致导致绑定失败(经典报错 `Invalid bound statement`,值得亲手踩一次)。

#### 任务 1.6 题目新增/修改/删除(约 2h)

- **思路提示**:新增题目要同时写 `question` 和 `question_tag` 两张表 → `@Transactional` 包住;标签关联用 MyBatis `<foreach>` 批量插入;修改题目时先删旧关联再插新关联;删除是 `UPDATE is_deleted = 1`。
- **核心知识点**:Spring 事务(传播行为默认 REQUIRED、回滚规则默认只回滚 RuntimeException、**自调用失效**问题);`<foreach>` 批量插入 vs 循环单插的性能差异。
- **验收标准**:① 新增后两张表数据一致;② 人为在插标签前抛异常,验证 question 表也回滚了;③ 修改题目的标签集合后关联表正确;④ 删除后列表查不到但库里还在。
- **易踩的坑**:`@Transactional` 加在了 private 方法或被同类方法自调用(AOP 代理失效,事务不生效——**这是面试最高频事务题,必须亲手验证一次**);tagIds 为空数组时 `<foreach>` 生成非法 SQL。

#### 任务 1.7 题目分页查询(动态 SQL)(约 2~3h)

- **思路提示**:手写分页(不用 PageHelper):一条 `COUNT` + 一条 `LIMIT #{offset}, #{pageSize}`,封装进 `PageResult`。动态条件用 `<where>` + `<if>`;按 tagId 筛选需要 join `question_tag`。列表 VO 要带 categoryName 和 tags(思考:join 一次查 vs 分两次查在内存组装,我推荐后者,理由让你先想)。
- **核心知识点**:MyBatis 动态 SQL(`<where>` 自动去 AND);`#{}` vs `${}`(SQL 注入,必考);深分页问题(`LIMIT 100000,10` 为什么慢、游标/子查询优化);EXPLAIN 的 type/key/rows/Extra 字段。
- **验收标准**:① 各条件单独及组合筛选正确;② keyword 参数输入 `' OR 1=1 --` 不会注入;③ **用 EXPLAIN 验证按分类查询走了索引**,并回答 2.4 节的思考题(是否需要 `(category_id, create_time)` 联合索引,需要就改);④ 空结果返回空数组而不是 null。
- **易踩的坑**:keyword 拼接用了 `${}`;offset 计算错误(pageNum 从 0 还是 1 开始要统一);COUNT 和 SELECT 的 where 条件不一致导致 total 对不上。

#### 任务 1.8 题目详情 + 浏览量计数(约 1.5h)

- **思路提示**:详情接口读缓存 `cache:question:detail:{id}`,未命中查库回填;浏览量用 Redis `INCR` 记增量,写个 `@Scheduled` 定时任务(如每 5 分钟)把增量批量回写 MySQL 并删 key。
- **核心知识点**:为什么浏览量不直接 `UPDATE view_count = view_count + 1`(高频写库、行锁竞争);Redis 单线程为什么快;定时任务的单机语义。
- **验收标准**:① 连刷 10 次详情,Redis 计数为 10;② 等定时任务跑完,MySQL 的 view_count 增加 10 且 Redis key 已删除;③ 详情缓存命中时不查库(开 MyBatis SQL 日志验证)。
- **易踩的坑**:回写用"读 Redis 值 → UPDATE 覆盖"(丢失回写窗口内的新增量,应该用 `GETDEL` 或先删后写增量);缓存的详情里 view_count 是旧值——想清楚并接受这个不一致(浏览量不需要强一致,能说出这个取舍就是加分项)。

#### 任务 1.9 热门题目缓存(穿透/雪崩/击穿三件套)(约 2h)

- **思路提示**:热门题目 = 按 view_count 排序 Top10,整块缓存为 JSON。要求处理:雪崩(TTL 加随机抖动)、穿透(详情接口对不存在的 id 缓存空值 1 分钟)、击穿(热点 key 重建时用互斥锁,`SET NX` 即可,拿不到锁的请求短暂等待重试或返回旧值)。
- **核心知识点**:缓存三大经典问题的定义与解法全景(穿透:空值缓存/布隆过滤器;雪崩:过期打散/多级缓存;击穿:互斥锁/逻辑过期)——**这是 Redis 面试的必考主线**。
- **验收标准**:① 缓存命中率可通过日志观察;② 请求不存在的题目 id 两次,第二次不查库;③ 删掉热点 key 后用 20 个并发请求打详情接口,数据库日志里只有 1 次查询(锁生效)。
- **易踩的坑**:空值缓存的 TTL 设太长(数据新增后一直 404);互斥锁没设过期时间(重建线程挂了就死锁);锁的粒度做成了全局一把锁(应该按 key 加锁)。

#### 任务 1.10 答题记录 + 排行榜(约 2h)

- **思路提示**:提交答题 → 插 `answer_record` → 答对时 `ZINCRBY` 总榜和日榜(日榜 key 带日期,首次创建时设 7 天 TTL)。排行榜接口 `ZREVRANGE WITHSCORES` 取 TopN,再批量查用户昵称(注意:是一次 `IN` 查询,不是循环单查——N+1 问题)。
- **核心知识点**:ZSet 底层(跳表 + dict,为什么范围查询选跳表不选红黑树);MySQL 与 Redis 双写的一致性讨论(这里答案是:允许最终不一致,record 表可重建榜单);N+1 查询问题。
- **验收标准**:① 答题后榜单实时变化;② 并发提交 20 次答对,榜单分数恰好 +20(`ZINCRBY` 原子性);③ 排行榜接口只发出 1 条用户查询 SQL。
- **易踩的坑**:先 `ZSCORE` 再 `ZADD`(非原子,并发丢更新,必须用 `ZINCRBY`);循环里查用户表;日榜 TTL 每次都重设(应只在 key 首次创建时设置,思考怎么判断"首次")。

**阶段一完成标志**:上述接口全部可用 curl/Apifox 走通,我对你进行第一轮模块面试(题库 + Redis + JWT)。

---

### 阶段二:AI 模拟面试(预计 8 个工作单元)

#### 任务 2.1 Spring AI 接入打通(约 1.5h)

- **思路提示**:引入 `spring-ai-starter-model-deepseek`(或 openai starter 改 base-url 指向 DeepSeek,两者选一;通义千问用 spring-ai-alibaba,推荐 DeepSeek:OpenAI 兼容、文档多、便宜)。配置 api-key → 注入 `ChatClient.Builder` 构建 `ChatClient` → 写一个临时测试接口,输入一句话返回模型回复(同步、非流式)。
- **核心知识点**:Spring AI 的抽象分层(Model → ChatClient → Advisor);为什么用 Spring AI 而不是直接 HTTP 调用(可移植性:换模型只改配置;内置流式/结构化输出/RAG 支撑)。
- **验收标准**:测试接口能返回真实模型回复;api-key 放在 `application-local.yml` 且该文件在 `.gitignore` 里。
- **易踩的坑**:key 提交进 git(一旦 push 就要作废重申请);网络超时没配置(默认超时可能不够,面试官型长回答容易断)。

#### 任务 2.2 创建面试会话 + 面试官 Prompt 设计(约 2h)

- **思路提示**:`POST /api/interviews`:校验方向合法(枚举)→ 限制单用户进行中会话数(如最多 3 个,Redis 或 count 查询)→ 插 `interview_session` → 组装 system prompt(角色设定:资深 Java 面试官、追问式风格、每次只问一个问题、根据回答质量调整深度)→ 调模型生成开场白 → system + 开场白两条消息写入 Redis List 和 MySQL → 返回会话 id + 开场白。
- **核心知识点**:System prompt 工程(角色、约束、输出风格);会话状态机设计;为什么 system prompt 也要持久化(重建上下文时需要)。
- **验收标准**:① 创建后返回自然的开场白(自我介绍 + 第一个问题);② 不同 direction 的开场问题明显不同;③ 超出会话数限制返回 42900;④ Redis 里能看到 ctx list,MySQL 里有 2 条消息。
- **易踩的坑**:prompt 里没约束"一次只问一个问题",AI 一口气问五个;开场白生成失败时 session 已插库(思考:要不要事务?外部调用不能包进数据库事务——**长事务陷阱,面试考点**,正确做法是先插库,生成失败允许会话存在、开场白重试)。

#### 任务 2.3 多轮对话上下文管理 + Token 窗口(约 2~3h)

- **思路提示**:封装 `InterviewContextManager`:`append(sessionId, msg)` 写 Redis List 并续期;`load(sessionId)` 取消息列表,按"预算 token 数"从最新往回裁剪(token 估算可以先用粗算:中文约 1 字 ≈ 0.6 token,或字符数/2,不必精确),**system prompt 永远保留**。Redis 未命中(过期)时从 MySQL 重建。先在非流式接口上验证整个链路。
- **核心知识点**:LLM 上下文窗口的概念、为什么要裁剪(成本 + 上限);滑动窗口 vs 摘要压缩两种策略(本项目用窗口,能讲出摘要方案即可);缓存重建模式。
- **验收标准**:① 连续对话 5 轮,AI 能记住第 1 轮你说过的内容;② 人为把 token 预算调小,验证最早的 QA 被裁掉但 system prompt 还在(打日志看拼出的消息列表);③ 手动删掉 Redis key 再对话,上下文从 MySQL 恢复,AI 记忆不丢。
- **易踩的坑**:裁剪时把 system prompt 裁掉了(AI 人设消失);从 List 头部裁剪方向搞反(留下最旧的丢了最新的);多端并发对同一会话提问导致上下文交错(用 `interview:lock:{sessionId}`,SET NX,请求结束释放)。

#### 任务 2.4 SSE 流式输出(约 2~3h,核心难点)

- **思路提示**:两条路线选一:① `SseEmitter` + `chatClient.stream()` 的 Flux 订阅,在 `subscribe` 回调里 `emitter.send()`;② Controller 直接返回 `Flux<ServerSentEvent<String>>`(需引入 webflux 依赖,和 MVC 可共存)。**推荐 ①**,SseEmitter 在 MVC 体系内更直观、面试好讲(底层是异步 Servlet)。注意:AI 完整回答要在流结束时拼好落库,发 `done` 事件。
- **核心知识点**:SSE vs WebSocket vs 轮询(单向/双向、协议、重连);SseEmitter 底层的 Servlet 3 异步机制(为什么不占用 Tomcat 工作线程等待);响应式流的背压概念(了解级)。
- **验收标准**:① `curl -N` 能看到逐段吐出的 delta 事件;② 最后收到 done 事件且 MySQL 里 assistant 消息完整;③ 客户端中途断开(Ctrl+C),服务端捕获并正常清理(日志验证,消息该落库落库);④ AI 报错时收到 error 事件而不是连接裸断。
- **易踩的坑**:SseEmitter 超时时间默认 30s,长回答被掐断(设长一点或 0L);在拦截器场景下 SSE 的异步分发会二次进入拦截器(`preHandle` 对 ASYNC 分发的处理);流式过程中抛异常直接把连接断了,前端不知道发生了什么;忘记 `emitter.complete()`。

#### 任务 2.5 消息持久化与会话查询(约 1.5h)

- **思路提示**:用户消息在请求进来时同步落库;AI 消息在流结束回调里落库(注意这是异步线程,ThreadLocal 里没有用户信息了——提前把需要的值取出来传进闭包,考点!)。实现会话列表(分页,按 create_time 倒序)和会话详情(消息按 id 升序)。**越权校验:只能看自己的会话**。
- **核心知识点**:水平越权漏洞(把 URL 里的 id 换成别人的会话 id 试试——每个"按 id 查"的接口都必须校验归属,这是安全审计第一课);异步线程与 ThreadLocal 上下文传递。
- **验收标准**:① 用户 A 的 token 访问用户 B 的会话返回 40400 或 40300;② 会话列表分页正确;③ 流式回答完成后立刻查详情,最后一条 AI 消息完整。
- **易踩的坑**:异步回调里调用 `UserContext.get()` 拿到 null 或别人的值;越权校验只做了列表没做详情/chat/finish(每个入口都要)。

#### 任务 2.6 结束面试 + 结构化评价报告(约 2~3h)

- **思路提示**:`finish` 接口:状态校验(只有"进行中"能结束)→ 更新状态 → 加载全量对话 → 用单独的"评委 prompt"(不是面试官人设)要求输出 JSON:score/highlights/weaknesses/summary → 用 Spring AI 的 `BeanOutputConverter`(它会把 JSON Schema 拼进 prompt 并负责反序列化)→ 存 `interview_report` → 状态置 2。报告生成建议同步做但设好超时;讲得出异步化方案(线程池 + 前端轮询)更好。
- **核心知识点**:结构化输出的实现原理(Schema 注入 prompt + 解析,以及模型原生 JSON mode);幂等设计(重复调 finish 不能生成两份报告——数据库唯一索引 + 状态机双保险);大模型输出不可靠时的重试策略(解析失败重试 1 次,再失败返回 50001)。
- **验收标准**:① 报告字段完整且是合法 JSON 存入 JSON 列;② 对同一会话连续调 finish 两次,第二次直接返回已有报告(幂等);③ 已结束的会话再调 chat 返回业务错误;④ 人为让模型输出脏 JSON(改 prompt 测试),验证重试与兜底逻辑。
- **易踩的坑**:把评价报告和面试对话共用一个 system prompt(评委和面试官职责混淆,输出质量差);JSON 里模型输出了 markdown 代码块包裹(```json),裸 `ObjectMapper` 解析炸掉(BeanOutputConverter 处理了大部分,但要验证);长对话超出模型上下文导致报告接口 400(对超长对话先做窗口裁剪)。

#### 任务 2.7 AI 调用的健壮性(约 1.5h)

- **思路提示**:给 AI 调用统一加:超时配置、失败重试(仅幂等场景,流式对话不自动重试)、每用户对话频率限制(如 1 次/10 秒,Redis `SET NX EX` 或 INCR+EXPIRE)、模型降级预案(配置化的备用模型,能讲出思路即可,不强制实现)。
- **核心知识点**:限流算法对比(固定窗口/滑动窗口/令牌桶/漏桶,本项目固定窗口够用但要能讲出临界问题);外部依赖故障隔离的思想(超时必须显式设置,考点:没有超时的外部调用会拖垮线程池)。
- **验收标准**:10 秒内连发两次 chat,第二次返回 42900;把 api-key 改错,接口返回 50001 而不是 500 裸异常。
- **易踩的坑**:限流 key 忘了带 userId(变成全局限流);INCR 和 EXPIRE 非原子(第一次 INCR 后崩溃,key 永不过期——用 lua 或 `SET NX EX`)。

**阶段二完成标志**:完整走通"创建会话 → 多轮流式对话 → 结束 → 查看报告",我进行第二轮模块面试(AI 工程 + SSE + 异步)。

---

### 阶段三:RAG 知识库(预计 6 个工作单元)

> 开工前先阅读：[智面 RAG 阶段三：项目定位、实现原理与新手学习路线](./RAG在智面项目中的定位、实现与新手学习路线.md)。这份文档结合当前项目说明 RAG 的业务定位、用户体验、代码边界、完整实现顺序和学习路径。

#### 任务 3.0 向量库选型(你拍板)

三个候选的对比,**我的推荐是 Redis Stack**,理由在最后:

| 维度 | Redis Stack(RediSearch) | Milvus | pgvector |
|------|--------------------------|--------|----------|
| 部署成本 | 你已有 Redis,换成 redis-stack 镜像即可,零新组件 | 独立部署,依赖 etcd/MinIO(standalone 也重) | 需引入 PostgreSQL(项目用的是 MySQL,等于加一个库) |
| Spring AI 支持 | `RedisVectorStore` 官方支持 | `MilvusVectorStore` 官方支持 | 官方支持 |
| 能力上限 | 千万级以下向量够用,HNSW 索引 | 专业向量库,亿级、多副本、GPU | 中等,百万级 |
| 面试话题性 | 能讲"Redis 不只是缓存"(Search/JSON 模块) | 能讲专业向量库架构,但容易被追问部署细节 | 话题性一般 |
| 学习负担 | 最低 | 最高 | 中 |

**推荐理由**:实习面试项目,RAG 的考点在"切分-向量化-召回-重排-引用"这条链路,不在向量库运维。Redis Stack 让你把精力花在链路上,还顺带加深 Redis 理解。如果你想额外卷一个"用过 Milvus"的标签,可以最后再平移(Spring AI 的 `VectorStore` 接口是统一的,切换成本很低——这本身就是接口抽象价值的活例子)。

#### 任务 3.1 向量库与 Embedding 打通(约 1.5h)

- **思路提示**:Redis 换 redis-stack 镜像;引入 `spring-ai-starter-vector-store-redis` + embedding 模型(DeepSeek 无 embedding API,用通义 text-embedding 或其他兼容服务,这里会有一次小选型);写测试接口:存 3 条文本 → 用相近语义的 query 搜索 → 验证相似度排序符合直觉。
- **核心知识点**:Embedding 是什么(文本→高维向量,语义相近则余弦距离近);向量索引 HNSW vs FLAT(近似 vs 精确,速度换精度)。
- **验收标准**:"线程池参数"能召回"ThreadPoolExecutor 构造函数"相关文本且分数最高。
- **易踩的坑**:embedding 模型和查询模型混用维度不一致;中文文本没验证过 embedding 效果就大量入库。

#### 任务 3.2 文档导入:切分 + 向量化 + 入库(约 2~3h)

- **思路提示**:上传接口收 markdown/txt(multipart)→ 插 `knowledge_doc`(status=0)→ 丢线程池异步处理:按标题/段落切分(Spring AI 的 `TokenTextSplitter` 或自写按 `##` 切,建议 chunk 300~500 token、重叠 50)→ 每个 chunk 存 MySQL(`knowledge_chunk`)+ 向量库(metadata 带 docId、chunkId)→ 更新 status=1。失败置 2 并记录原因。
- **核心知识点**:RAG 的 ETL 流水线;chunk 大小的权衡(太小语义破碎、太大召回不精准且费 token);为什么要 overlap;异步任务的状态回写。
- **验收标准**:① 上传一份八股 md,轮询状态到"已完成";② chunk_count 与向量库条数一致;③ 处理线程抛异常时状态置 2,不会永远卡在 0。
- **易踩的坑**:在 Controller 里同步做向量化(大文档把请求打超时);线程池用 `Executors.newFixedThreadPool` 随手创建且无界队列(OOM 隐患,自定义 `ThreadPoolExecutor` 并说得出七个参数——把八股用在了实处);异常没捕获导致状态永远是"处理中"。

#### 任务 3.3 检索接口与相似度阈值(约 1.5h)

- **思路提示**:`/api/knowledge/search`:query → embedding → `vectorStore.similaritySearch(topK, threshold)` → 关联 `knowledge_chunk`/`knowledge_doc` 拼出处。阈值(如 0.7)很重要:低于阈值宁可返回空,不硬凑。
- **核心知识点**:召回率 vs 准确率的权衡;为什么需要相似度阈值(RAG 幻觉的第一道闸)。
- **验收标准**:知识库里有的问题返回相关切片+出处;完全无关的 query(如"今天天气")返回空数组。
- **易踩的坑**:不设阈值,无关内容也进 prompt(垃圾进垃圾出);topK 设太大浪费 token。

#### 任务 3.4 答错联动:RAG 参考答案(约 2h)

- **思路提示**:`/api/questions/{id}/reference`:取题目 title 作 query → 检索 topK=3 → 组装 RAG prompt("仅根据以下资料回答,资料不足就说不知道:{chunks} 问题:{title}")→ 调模型 → 返回答案 + sources(含原文片段与文档标题)。检索为空时降级:直接返回题库自带 answer,标注"未命中知识库"。
- **核心知识点**:RAG 完整链路(为什么能减少幻觉:把开卷考试的"卷"塞给模型);prompt 注入的防范意识(知识库内容里若有指令性文本怎么办——了解级)。
- **验收标准**:① 知识库覆盖的题目返回带出处的答案;② 未覆盖的题目走降级路径;③ 答案内容确实基于召回片段(人工抽查)。
- **易踩的坑**:把 topK 个 chunk 全文无脑塞进 prompt 不做长度控制;sources 返回了 chunk 全文但没有 docTitle(引用没意义)。

#### 任务 3.5 RAG 效果调优与评估(约 2h,可选但推荐)

- **思路提示**:准备 10 个测试问题 + 期望命中的文档,写个简单脚本统计召回命中率;调整 chunk 大小/overlap/topK/阈值,记录对比数据。这份数据写进简历极加分("通过调整分块策略将召回命中率从 X 提升到 Y")。
- **核心知识点**:RAG 评估思路(召回质量与生成质量分开评);参数调优的实验方法论。
- **验收标准**:产出一张参数对比小表格,写进 `docs/` 里。
- **易踩的坑**:凭感觉调参不记录数据,简历上只能写"做了 RAG"而说不出效果。

**阶段三完成标志**:第三轮模块面试(RAG 原理 + 向量检索 + 工程化)。

---

### 阶段四(可选):错题本 + Function Calling

#### 任务 4.1 错题本(约 1.5h)

- **思路提示**:改造任务 1.10 的答题接口:答错时 `INSERT ... ON DUPLICATE KEY UPDATE`;分页查询 join 题目表;"标记已掌握"更新 status(校验归属!)。
- **核心知识点**:`ON DUPLICATE KEY UPDATE` 的原理与幂等写法;水平越权复习。
- **验收标准**:同一题答错 3 次 wrong_count=3 且只有一条记录;标记别人的错题被拒。
- **易踩的坑**:先查后插的并发竞态(又一次!这次要条件反射地想到唯一索引方案)。

#### 任务 4.2 Function Calling 生成复习计划(约 2~3h)

- **思路提示**:用 Spring AI 的 `@Tool` 注解定义两个工具:`getWrongQuestionStats(userId)`(按分类统计错题分布)、`getWeakestTopics(userId, limit)`;`/api/study-plans/generate` 的 prompt 要求 AI:先调用工具了解薄弱分布,再生成 7 天复习计划(结构化 JSON),存库返回。
- **核心知识点**:Function Calling 原理(模型返回"要调用的函数+参数" → 框架执行 → 结果回传模型继续生成,模型本身不执行代码——必考!);它与 RAG 的互补关系(RAG 给静态知识,FC 给实时数据/动作)。
- **验收标准**:① 日志能看到工具被真实调用及传参;② 生成的计划确实围绕错题集中的分类;③ 没有错题的用户得到合理兜底文案。
- **易踩的坑**:工具方法里拿不到 userId(工具执行也在框架回调链路里,用户身份要通过 ToolContext 或闭包传入,不能依赖 ThreadLocal);工具描述(description)写得含糊导致模型不调用或乱传参。

---

## 6. 面试考点地图

> 每个模块做完后,对照这张表自测:每一条都应该能脱稿讲 2 分钟。⭐ 是超高频。

### 用户与鉴权模块

- ⭐ JWT 的组成、签名原理,和 Session 方案的对比(各自优劣、什么场景选什么)
- ⭐ JWT 如何主动失效/续期?(黑名单、双 token,结合你的实现讲)
- ⭐ 密码为什么用 BCrypt 不用 MD5+盐?盐存在哪?彩虹表是什么?
- ⭐ 拦截器和过滤器的区别?你为什么选拦截器?
- ⭐ ThreadLocal 原理、内存泄漏的成因(Entry 的弱引用 key)、你在项目里怎么保证 remove?
- 水平越权是什么?你的项目哪些接口有这个风险,怎么防?
- 并发注册同名用户怎么防?(唯一索引兜底 + 异常转译)

### 题库模块(MySQL 主战场)

- ⭐ 你的题目表建了哪些索引?为什么?(能画 B+ 树、讲聚簇/二级索引、回表)
- ⭐ 联合索引最左前缀原则,结合你 `(category_id, create_time)` 的实验讲
- ⭐ EXPLAIN 你用过吗?讲讲 type、key、rows、Extra 里的 filesort
- ⭐ `#{}` 和 `${}` 的区别,SQL 注入怎么发生的?
- ⭐ 事务失效的场景(自调用、private、异常被吞、非 RuntimeException)——你踩过哪个?
- 逻辑删除和唯一索引怎么共存?
- 深分页为什么慢?怎么优化?
- 为什么不用外键约束?
- MyBatis 一二级缓存(以及为什么生产一般关掉二级缓存)

### Redis 模块

- ⭐ 缓存穿透/雪崩/击穿的区别与解法——直接讲你任务 1.9 的实现
- ⭐ 缓存和数据库的一致性:你为什么用先更库再删缓存?延迟双删听过吗?
- ⭐ ZSet 底层为什么用跳表不用红黑树?
- ⭐ Redis 为什么快?(内存、单线程无锁、IO 多路复用)
- 浏览量为什么先记 Redis 再定时回写?丢数据了能接受吗?
- INCR/EXPIRE 非原子怎么解决?(SET NX EX、lua)
- Redis 过期删除策略(惰性+定期)和内存淘汰策略(LRU/LFU)
- 分布式锁:你会话锁用 SET NX 实现的,有什么缺陷?(误删、不可重入 → 引出 Redisson 话题)

### AI 面试模块(项目亮点)

- ⭐ 多轮对话的上下文怎么管理的?token 窗口怎么裁剪?为什么 system prompt 不能裁?
- ⭐ SSE 和 WebSocket 的区别?为什么选 SSE?SseEmitter 底层是什么?(异步 Servlet,不阻塞工作线程)
- ⭐ 怎么让大模型输出可靠的结构化 JSON?解析失败怎么办?
- ⭐ Redis 和 MySQL 双写消息,为什么这么设计?一致性怎么考虑?
- 外部 AI 调用为什么不能放进数据库事务?超时怎么设置?
- 异步线程里 ThreadLocal 丢失问题你怎么解决的?
- 你的限流怎么做的?固定窗口的临界缺陷是什么?
- 幂等:重复点"生成报告"怎么保证只有一份?

### RAG 模块(区分度最高)

- ⭐ RAG 完整链路讲一遍(导入→切分→向量化→检索→增强生成→引用)
- ⭐ chunk 大小怎么定的?overlap 为什么需要?你调优的数据是什么?
- ⭐ 向量相似度怎么算的?HNSW 是什么思路?(近似最近邻、跳表式分层——和 ZSet 跳表呼应!)
- 为什么选 Redis Stack 不选 Milvus?(讲你的选型对比)
- 相似度阈值起什么作用?RAG 怎么减少幻觉?
- 删除文档时 MySQL 和向量库的一致性怎么处理?

### Function Calling 模块

- ⭐ Function Calling 的原理?模型真的执行了你的代码吗?(没有!)
- FC 和 RAG 各解决什么问题?
- 工具的入参安全:模型传来的参数你敢直接用吗?(校验 + 用户身份不由模型提供)

### 通用工程素养(简历上的"软亮点")

- 统一返回体 + 全局异常处理的设计(`@RestControllerAdvice` 原理)
- 参数校验体系(JSR 380)
- 你项目的分层原则,Entity/DTO/VO 为什么分开
- 线程池七参数 + 拒绝策略(你在文档导入用了自定义线程池)
- 如果这个项目要支撑 10 倍流量,你会先改哪里?(准备一个演进故事:读写分离/缓存预热/会话服务拆分/消息队列削峰)

---

## 附:执行顺序备忘

1. ✅ 脚手架初始化(导师完成)→ 你跑通任务 1.0
2. 按任务编号顺序推进,**每完成一个任务把代码发我 review**
3. 每阶段结束进行模块面试
4. 阶段一结束后建议 git init 并保持提交习惯(commit message 用 `feat: 完成用户注册接口` 风格,面试官会看提交历史)
