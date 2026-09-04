# 智面 ZhiMian

智面是一个 AI 面试刷题平台，采用 Spring Boot + MyBatis + MySQL + Redis + Spring AI 作为后端，Vue 3 + TypeScript + Vite + Element Plus 作为前端。

## 功能模块

- 用户注册、登录、JWT 鉴权、登出黑名单。
- 题库、分类、标签、热门题目与浏览量统计。
- 答题记录、排行榜。
- AI 模拟面试、多轮上下文、SSE 流式输出、结构化面试报告。
- 面试历史查询与报告展示。

## 目录结构

```text
src/main/java/com/zhimian/    后端源码
src/main/resources/mapper/    MyBatis XML
frontend/                     前端工程
sql/                          数据库初始化与演示数据
docs/                         设计说明、学习资料、优化建议
阶段部署/                     分阶段开发记录
```

更完整的结构说明、清理记录和后续规划见 `docs/项目结构与优化建议.md`。

## 后端启动

1. 创建 MySQL 数据库并执行 `sql/init.sql`。
2. 按需执行 `sql/test-data.sql` 和 `sql/import-interview-qa.sql`。
3. 在 `src/main/resources/application-local.yml` 中配置数据库密码、Redis 密码、AI Key 等本地敏感配置。
4. 启动后端:

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

前端 API 默认通过 `/api` 访问后端，代理配置见 `frontend/vite.config.ts`。

## 质量检查

```bash
mvn test
cd frontend
npm run build
```

当前项目还缺少自动化测试源码，后续应优先补齐后端 Service/Controller 测试和前端关键页面构建检查。

## 维护约定

- 临时接口、一次性 smoke 页面、未引用 mock 数据完成阶段任务后及时删除。
- 学习资料放入 `docs/`，阶段实现记录放入 `阶段部署/`，项目入口说明保持简短。
- 敏感配置不进入 Git，使用 `application-local.yml` 或环境变量维护。
