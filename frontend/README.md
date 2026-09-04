# 智面前端

智面前端使用 Vue 3、TypeScript、Vite、Pinia、Vue Router 和 Element Plus 构建，主要承载题库浏览、排行榜、AI 模拟面试、面试记录等页面。

## 本地开发

```bash
npm install
npm run dev
```

开发服务器默认通过 Vite 启动，接口请求在 `src/api/http.ts` 中统一配置，默认以 `/api` 作为后端前缀。

## 构建

```bash
npm run build
```

构建前会先执行 `vue-tsc -b` 做类型检查，再输出静态资源到 `dist/`。

## 目录说明

- `src/api/`: 后端接口封装。
- `src/views/`: 页面级组件。
- `src/components/`: 可复用业务组件。
- `src/stores/`: Pinia 状态管理。
- `src/types/`: 前端类型定义。
- `src/mock/`: 暂未接后端接口的页面演示数据，后续接入真实接口后应逐步删除。
