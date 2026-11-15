# 智面 ZhiMian

基于 Spring Boot、Spring AI 和 Vue 3 的面试训练平台，支持题库练习、AI 面试、流式对话、面试报告、错题本、收藏夹、排行榜和 RAG 知识库。

## 本地启动

需要 Java 17、Maven 3.9、Node.js 20.19 或更高版本，以及已经运行的 Docker。

```bash
git clone https://github.com/a2874995779-del/ZhiMian.git
cd ZhiMian
docker compose up -d --wait
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

新开一个终端，在项目的 frontend 目录执行以下命令。

```bash
npm ci
npm run dev
```

浏览器打开 Vite 输出的地址，通常是 http://localhost:5173。后端端口为 8080，健康检查为 http://localhost:8080/api/health。演示管理员为 admin / admin123，也可以通过界面注册普通用户。

首次启动 MySQL 容器时自动执行 sql/init.sql 和 sql/test-data.sql。init.sql 包含当前完整表结构，migrate-*.sql 用于旧数据库升级，新数据库无需重复执行这些 ALTER 脚本。test-data.sql 会重建题库演示数据，适用于空的本地开发数据库。

## 模型与知识库配置

dev 配置支持先启动服务并使用注册、登录、题库、错题、收藏等基础功能。真实 AI 请求需要有效的模型服务凭据。

- OPENAI_API_KEY、OPENAI_BASE_URL、OPENAI_CHAT_MODEL 配置默认聊天模型，默认地址与模型为 DeepSeek。
- 用户也可以在“模型设置”页面配置自己的聊天模型与 Key。
- EMBEDDING_API_KEY、EMBEDDING_BASE_URL、RAG_EMBEDDING_MODEL 配置 Embedding，默认模型为 BAAI/bge-m3。
- 配置好 Embedding 后，设置 RAG_INITIALIZE_SCHEMA=true 并重启后端，初始化向量索引。初始化过程需要调用 Embedding 服务取得向量维度。后续更换向量维度时应使用新的索引名称。
- 本地 MySQL 使用 23307 端口，Redis Stack 使用 26380 端口。它们仅绑定本机，账号与 JWT 开发密钥位于 application-dev.yml。
- 使用现有服务时，通过 SPRING_DATASOURCE_URL、SPRING_DATASOURCE_USERNAME、SPRING_DATASOURCE_PASSWORD、SPRING_DATA_REDIS_HOST、SPRING_DATA_REDIS_PORT 覆盖参数。
- 自定义部署应设置独立的 ZHIMIAN_JWT_SECRET，并保持签名密钥稳定，以便读取已加密的用户模型 Key。

docker compose stop 停止基础服务，正常重启会保留数据库命名卷中的数据。docker-compose.rag.yml 可用于只启动原有 6380 端口的知识库 Redis，与完整本地启动方案按需选择。

## 目录与检查

后端源码位于 src/main/java/com/zhimian，MyBatis 映射位于 src/main/resources/mapper，前端位于 frontend，建表和升级脚本位于 sql。

```bash
mvn test
cd frontend
npm run build
```

外部数据库与 Redis 集成测试需要对应的测试环境变量和服务。普通单元测试不需要真实模型 Key。
