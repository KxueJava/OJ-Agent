# CodeAgent OJ

面向算法学习与在线评测的智能编程平台：保留 OJ 的确定性（沙箱执行、用例隔离、可复现判题），
同时把 Agent 深度嵌入题目理解、调试、代码审查与个性化训练流程 —— 只在需要的阶段给**逐级提示**，不直接泄露答案。

项目方案见 [`docs/project-plan.md`](docs/project-plan.md)，前端页面设计见 [`docs/frontend-design.md`](docs/frontend-design.md)；
竞赛方案与状态见 [`docs/contest-plan.md`](docs/contest-plan.md)、[`docs/contest-status.md`](docs/contest-status.md)。
可打开的前端原型：竞赛 [`prototype/contest-acm.html`](prototype/contest-acm.html)、讨论区 [`prototype/discussion.html`](prototype/discussion.html)。

> **当前状态：核心功能已实现并端到端验证**（不再是方案阶段）。已实现/未实现的部分在文末如实列出。

## 产品目标

- 保留 OJ 的确定性：沙箱执行、测试用例隔离、可复现的判题结果。
- 增加 Agent 的即时辅导：分阶段提示，而不是直接给可提交的完整代码。
- 将一次做题沉淀为可持续的学习路径：错题归因、薄弱点与复习计划可追踪。

## 功能一览

| 模块 | 能力 |
|---|---|
| **题库** | 124 道题；题面含「输入格式/输出格式」，示例与判题用例格式一致；关键词/难度/标签筛选、分页、收藏、AC 标记 |
| **判题** | Docker 沙箱真实编译运行；Java 21 / C++17 / C17；记录运行时间、内存、编译耗时；失败分类（用户/基础设施/内部） |
| **提交** | 列表与详情（语言、源码、逐用例结果）、单条重判、按题目版本批量重判、SSE 实时状态、提交限流、outbox 投递与死信观测 |
| **竞赛** | 管理端：建草稿/改配置/选题/发布（**发布前逐条校验**）/取消/定榜/竞赛内重判/公告；公开端：列表、详情、报名（幂等）、**ACM 罚时实时榜单**、**封榜冻结**、定榜快照、竞赛工作台、「我的竞赛」 |
| **讨论区** | 题解/提问/公告/闲聊分类、搜索与排序、发帖/回复/**采纳**、管理端置顶/锁定/删除（写审计）、**Agent 摘要**（只读） |
| **Agent** | Supervisor → Debugger∥Reviewer → Safety → Finalizer 多智能体链路、DB 对话记忆、工具白名单（租户隔离）、SSE 流式、输出安全审核、自动诊断（节流/重试上限/死信）、会话历史、Markdown 渲染、**赛中禁用**、**每日配额** |
| **学习闭环** | 错题本（失败提交自动归因）、今日训练推荐、学习计划、排行榜、个人资料与头像 |
| **工程** | Flyway 迁移 V1–V35、统一错误响应（带 `detail`）、管理审计表、Windows 一键验收脚本、全站中英双语 |

## 技术栈

- **Web**：Next.js 15（App Router）+ React 19 + TypeScript + CSS Modules（暖色视觉：`#f3f1eb` 底 / `#d37737` 品牌橙）
- **后端**：Java 21 + Spring Boot 3.4，Maven 多模块；数据访问以 `JdbcTemplate` 的显式 SQL 为主，身份模块使用 MyBatis-Plus Mapper
- **数据与消息**：MySQL（开发库 `codeagent_oj_local`）+ Redis + RabbitMQ；写库与投递采用 **outbox 模式**
- **判题**：独立 `oj-judge-worker`，消费队列 → 调 Docker 沙箱 → 回写结果
- **Agent**：Spring AI + DeepSeek（`DEEPSEEK_API_KEY`），工具白名单 + `ToolContext` 租户隔离，流式响应与审计
- **部署**：根目录 `compose.yaml` 起本地依赖（MySQL/Redis/RabbitMQ）；生产编排待补

## 阶段与工程骨架

- 前端：`frontend/`，Next.js + TypeScript
- 后端：`backend/`，Java 21 + Spring Boot 的 Maven 多模块工程（`codeagent-oj-server`、`oj-judge-worker`）
- 本地依赖：根目录 `compose.yaml` 启动 MySQL、Redis 和 RabbitMQ
- 开发数据库默认 `codeagent_oj_local`，避免影响机器上已有的 `codeagent_oj`

### 本地启动顺序

```powershell
docker compose up -d
$env:JAVA_HOME = 'D:\Java\JDK21'

cd backend
mvn -s .mvn/settings.xml -B -ntp -DskipTests -pl codeagent-oj-server package
java -jar codeagent-oj-server/target/codeagent-oj-server-0.0.1-SNAPSHOT.jar     # 首次启动自动迁移到 V35

mvn -s .mvn/settings.xml -B -ntp -DskipTests -pl oj-judge-worker package
java -jar oj-judge-worker/target/oj-judge-worker-0.0.1-SNAPSHOT.jar             # 另开终端

cd ..\frontend
Copy-Item .env.local.example .env.local
npm install
npm run dev                                                                     # http://localhost:3000
```

- 后端健康检查 `http://localhost:8080/api/health`，Agent 自检 `http://localhost:8080/api/agent/health`，
  OpenAPI `http://localhost:8080/swagger-ui.html`
- 本地 RabbitMQ 默认 `guest/guest`（仅本机可连）
- 判题需要沙箱镜像（含 JDK + g++/gcc），构建脚本见 `tools/sandbox/`；worker 需要访问宿主 Docker
- 前端 API 地址取自 `NEXT_PUBLIC_API_BASE_URL`（默认 `http://localhost:8080`）

### 第一个管理员

注册后把账号提升为 ADMIN（**提升后必须重新登录**，角色写在 JWT 里）：

```powershell
$jar = (Get-ChildItem "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j" -Recurse -Filter '*.jar' |
        Sort-Object FullName -Descending | Select-Object -First 1).FullName
java -cp $jar tools/JudgeAdmin.java admin <你的用户名>
```

管理员入口 `/admin`（题目管理 / 用户管理 / **竞赛管理**）。登录字段是 **identifier**（用户名或邮箱均可）。

## 数据库迁移

- 迁移目录 `backend/codeagent-oj-server/src/main/resources/db/migration/`，当前最新 **V35**
- `V14` 在早期已应用但文件缺失：本机通过移除该历史记录恢复启动
  （`DELETE FROM flyway_schema_history WHERE version='14'`）。类似
  `Detected applied migration not resolved locally` 可用 [`tools/FlywayRepair.java`](tools/FlywayRepair.java) 处理
- 迁移失败会阻塞启动；修复后请**先停进程再 `mvn clean package`** —— `target/` 里残留的旧迁移会被打进包，
  表现为"明明删了文件却仍报错"
- 批量出题见 [`tools/gen-problems/`](tools/gen-problems/)：它会**编译并运行每道题的参考解**，用其 stdout 作为期望输出，
  从而保证题面 / 示例 / 判题数据 / 参考解四者自洽

## 主要接口

| 领域 | 端点 |
|---|---|
| 认证 | `POST /api/auth/register`、`/login`、`/refresh`、`/logout`；`GET /api/auth/me` |
| 题库 | `GET /api/problems`（`query/difficulty/tag/page/size/favoriteOnly`）、`GET /api/problems/{slug}`、`PUT /api/problems/{slug}/favorite`、`GET /api/tags` |
| 工作台 | `POST /api/workspace/runs`、`GET /api/workspace/runs/{id}`、`GET /api/workspace/history` |
| 提交 | `POST /api/submissions`、`GET /api/submissions`、`GET /api/submissions/{id}`、`POST /api/submissions/{id}/rejudge`、`GET /api/submissions/solved`、`GET /api/submissions/{id}/events`（SSE） |
| 竞赛（公开） | `GET /api/contests`、`GET /api/contests/{slug}`、`POST /api/contests/{slug}/register`、`GET /api/contests/{slug}/standings`、`GET /api/contests/mine`、`GET /api/contests/active`（Agent 锁判定） |
| 竞赛（管理） | `POST/PUT /api/admin/contests`、`/{id}/problems`、`/{id}/publish\|cancel\|finalize\|rejudge\|announcements`、`GET /{id}/standings` |
| 讨论区 | `GET/POST /api/discussions`、`GET /api/discussions/{id}`、`POST /{id}/posts`、`POST /{id}/posts/{postId}/accept`、`POST /{id}/pin\|lock\|summary`、`DELETE /{id}` |
| Agent | `POST /api/agent/ask`、`GET /api/agent/stream`（SSE）、`GET /api/agent/health`、`GET /api/agent/usage`、`GET /api/agent/sessions`、`GET/DELETE /api/agent/sessions/{id}`、`GET /api/agent/submissions/{id}/diagnosis` |
| 学习 / 排行 | `GET /api/learning/overview`、`/mistakes`、`/plan`、`POST /api/learning/mistakes/{id}/review`、`GET /api/leaderboard` |

错误响应统一为 `ProblemDetail`，例如
`{"status":403,"detail":"比赛进行中（333），期间不能发布或回复该赛题的讨论"}`，前端直接展示 `detail`。

## Agent 说明

**链路**：Supervisor（分派意图）→ Debugger ∥ Reviewer（并行分析）→ Safety（输出侧审核）→ Finalizer（收尾成文）。

**几条刻意的产品规则**

1. **赛中禁用**：当「我已报名 + 比赛进行中 + 这道题正是该场赛题」同时成立时，手动提问 403、自动诊断不再生成；
   判定完全在服务端（与 `GET /api/contests/active?problemSlug=` 同源），前端（桌宠、工作台、`/agent/[slug]`、首页卡片）只是 UX。
2. **每日配额**：默认每人每天 20 次（`app.agent.daily-limit`），`agent_usage_daily` 表按自然日**原子计数**，
   超限返回 429 并给出明确文案，跨天自动重置。
3. **讨论区摘要**：只读，由楼主或管理员触发；赛中关联赛题的帖子不允许生成，避免变成变相提示。
4. **密钥自检**：未配置模型 Key 时应用仍能启动（Agent 给出明确错误），`/api/agent/health` 一眼确认。

## 验证方式

```powershell
# 后端单测（49 个用例，含榜单算法 5 个）
cd backend ; mvn -B -ntp test

# 前端类型检查
cd frontend ; npx tsc --noEmit

# 竞赛端到端验收（P1~P5，含真实判题）
tools\contest-verify-all.ps1 -ApiKey $env:DEEPSEEK_API_KEY
```

`tools/` 下的验收脚本会**真实启动服务、调接口、跑真实判题**，并把结果同时写到 `*.txt` 便于留档；
测试数据可用 [`tools/clean-test-data.ps1`](tools/clean-test-data.ps1)（默认 dry-run，加 `-Apply` 才执行）清理。

## 已知限制与后续计划

- **题库**：100 道扩充计划完成 23 道（当前 124 道），其余 77 道待写（贪心 / DP / 搜索 / 图 / 数论 / 字符串）
- **判题**：仅 Java 21 / C++17 / C17；暂无 Python、暂无部分分、仅单文件 `Main`
- **讨论区**：管理端与摘要**后端**已完成并验证，前端按钮与摘要卡待接入；详情接口尚未返回已生成的摘要字段
- **导航**：共享导航组件（`components/site-nav.tsx`）已用于题库 / 收藏 / 讨论区页面，其余页面仍各写一份内联导航
- **部署**：`compose.yaml` 只起依赖服务；应用服务的一键编排与 CI 工作流待补
- **测试**：单测 49 + 4，端到端靠 `tools/` 脚本手工执行，尚未接入流水线
- **数据**：本机库中残留少量测试账号与测试帖，可用清理脚本处理

## 安全提醒

- `.env` 与 `*.log` 已在 `.gitignore` 中；**切勿把真实 `DEEPSEEK_API_KEY` 提交进仓库**
- 若密钥曾出现在聊天记录或日志里，请**轮换**后只在部署环境变量中配置
- 生产环境必须替换 `JWT_SECRET`（默认值仅供开发）与数据库默认口令；建议为 Redis / RabbitMQ 配置认证
- 判题 worker 需要访问宿主 Docker，请按最小权限部署并隔离网络
