# CodeAgent OJ

面向算法学习与在线评测的智能编程平台，产品形态类似 LeetCode，但把 Agent 深度嵌入题目理解、调试、代码审查和个性化训练流程。

项目方案见 [`docs/project-plan.md`](docs/project-plan.md)，前端页面设计见 [`docs/frontend-design.md`](docs/frontend-design.md)。

可直接打开的前端原型位于 [`prototype/home.html`](prototype/home.html) 和 [`prototype/index.html`](prototype/index.html)。

## 产品目标

- 保留 OJ 的确定性：沙箱执行、测试用例隔离、可复现的判题结果。
- 增加 Agent 的即时辅导：只在用户需要的阶段提供逐级提示，而不是直接泄露答案。
- 将一次做题沉淀为可持续的学习路径：薄弱知识点、错误模式和复习计划可追踪。

## 技术栈

- Web：Next.js + TypeScript + Monaco Editor
- 后端：Java 21 + Spring Boot 3 + MyBatis-Plus
- 数据：MySQL 8.0 + Redis
- 消息队列：RabbitMQ
- 判题：独立 Java Judge Worker，Docker/gVisor 隔离
- Agent：Spring AI 多 Agent 编排、模型网关 + 工具调用层，支持流式响应与审计
- 部署：Docker Compose（开发）→ Kubernetes（生产）

## 文档状态

当前仓库处于方案设计阶段，优先按文档中的 8 周 MVP 计划拆分实现。Agent 层采用 Supervisor 协调的多 Agent 架构。

## 阶段 0 工程骨架

- 前端：`frontend/`，Next.js + TypeScript。
- 后端：`backend/`，Java 21 + Spring Boot 的 Maven 多模块工程。
- 本地依赖：根目录 `compose.yaml` 启动 MySQL、Redis 和 RabbitMQ。
- 本项目默认开发数据库为 `codeagent_oj_local`，避免影响机器上已有的 `codeagent_oj` 数据库。

本地启动顺序：

```powershell
docker compose up -d
$env:JAVA_HOME = 'D:\Java\JDK21'
cd backend
mvn -s .mvn/settings.xml spring-boot:run -pl codeagent-oj-server
cd ..\frontend
Copy-Item .env.local.example .env.local
npm install
npm run dev
```

后端健康检查为 `http://localhost:8080/api/health`，OpenAPI 为 `http://localhost:8080/swagger-ui.html`。

本地 RabbitMQ 默认使用 `guest/guest`，仅允许本机连接；提交队列阶段再创建专用应用用户。
