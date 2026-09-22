# 智面 ZhiMian

智面是一个基于 Spring Boot、Spring AI 和 Vue 3 的 AI 智能面试训练平台，支持专项练习、综合场景面试、SSE 流式对话、面试报告、错题本、收藏夹和 RAG 知识库。

## 技术栈

- 后端：Java 17、Spring Boot、MyBatis、MySQL、Redis、Spring AI
- 前端：Vue 3、TypeScript、Vite、Element Plus
- AI：ChatModel、Embedding、Redis Vector Store、SSE
- 部署：Docker Compose

## 目录

```text
src/main/java/com/zhimian/    后端源码
src/main/resources/mapper/    MyBatis XML
frontend/                     前端工程
sql/                          数据库脚本
```

## 后端启动

1. 创建 MySQL 数据库并执行 `sql/init.sql`。
2. 按需执行 `sql/migrate-*.sql` 和演示数据脚本。
3. 在 `src/main/resources/application-local.yml` 中配置数据库、Redis、JWT 和模型服务参数。
4. 启动后端：

```bash
mvn spring-boot:run
```

默认端口为 `8080`。

## 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认通过 `/api` 代理访问后端。

## 检查

```bash
mvn test
cd frontend
npm run build
```
