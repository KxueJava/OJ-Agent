# CodeAgent OJ 项目方案

## 1. 项目定义

CodeAgent OJ 是一个“在线评测 + AI 教练”的算法学习平台。用户在题目页阅读约束、编写代码、提交评测，并可以在不离开当前上下文的情况下请求理解提示、错误定位、复杂度分析和个性化复盘。

核心原则：**判题结果由 OJ 决定，学习建议由 Agent 生成；两者职责隔离。** Agent 永远不能修改判题结果，也不能绕过权限读取隐藏测试用例。

## 2. 目标用户与场景

| 用户 | 高频问题 | 产品响应 |
| --- | --- | --- |
| 入门学习者 | 看不懂题意、卡在第一步 | 题意重述、关键词解释、逐级提示 |
| 有基础的刷题者 | 代码能跑但过不了边界 | 失败样例归因、边界检查、复杂度建议 |
| 面试准备者 | 训练无体系、重复犯错 | 按标签和错误模式生成训练计划 |
| 教师/团队 | 难以了解学习过程 | 进度、知识点掌握度和提交分析 |

## 3. MVP 范围（8 周）

### 必须交付

1. 账号、题库浏览、标签/难度/状态筛选。
2. 题目详情、Markdown 描述、示例、约束、代码编辑器和多语言模板。
3. 提交代码、队列状态、编译日志、通过/失败结果、运行时间和内存。
4. 基础 Agent：题意解释、三阶段提示（思路 → 关键步骤 → 伪代码）、错误诊断、复杂度分析。
5. 提交历史、错题本、知识点标签和简单周训练计划。
6. 管理端题目录入、测试点上传、题目版本发布。

### 暂不纳入

实时对战、题解社区、企业招聘流程、自动生成并直接发布题目。它们会显著增加审核、风控和运营成本。

## 4. 关键用户流程

### 做题与求助

题目页加载 → 选择语言/模板 → 编写并运行样例 → 提交 → Judge 排队 → 查看结果 → 选择 Agent 动作。

Agent 动作必须带上下文范围：

- “解释题意”：只使用题面和用户问题。
- “分析我的代码”：使用当前代码、编译输出和公开样例结果。
- “给我提示”：按 `hint_level=1/2/3` 逐级放开。
- “复盘”：使用本次提交、历史错误和知识点档案。

### 训练计划

提交事件和错误类型入库 → 聚合知识点掌握度 → 推荐下一道题 → 用户确认/跳过 → 每周复盘。

## 5. 系统架构

```text
Browser
  ├─ Next.js Web（题目、编辑器、Agent 面板、学习数据）
  └─ SSE/WebSocket（提交状态、Agent 流式输出）
          │
      API Gateway
          ├─ Auth / User Service
          ├─ Problem Service
          ├─ Submission Service ── Redis Queue ── Judge Worker
          │                                  └─ Sandbox Runner
          ├─ Learning Service
          └─ Multi-Agent Orchestrator ── Model Gateway
                                      ├─ Prompt/Policy Store
                                      └─ Tool Gateway（只读题面、代码、结果）
          │
   MySQL / Object Storage / Observability
```

### Java + Spring Boot 服务划分

首个版本建议以模块化单体启动，避免一开始引入微服务的部署复杂度；所有模块在同一个 Spring Boot 应用中，以明确包边界隔离。当提交量和模型调用量增长后，优先拆出 `judge-worker`，再拆 `agent-service`。

```text
codeagent-oj-server (Spring Boot 3 / Java 21)
  ├─ auth        Spring Security + JWT + RBAC
  ├─ problem     题库、标签、题目版本、管理端
  ├─ submission  提交创建、状态查询、SSE 推送
  ├─ learning    错题、掌握度、训练计划
  ├─ agent       Spring AI 多 Agent 编排、提示策略、工具白名单、审计
  ├─ infra       MyBatis-Plus、Redis、RabbitMQ、对象存储、异常处理
  └─ api         REST Controller、DTO、OpenAPI

oj-judge-worker (Spring Boot / Java 21)
  ├─ RabbitMQ Consumer
  ├─ Docker Sandbox Adapter
  ├─ Compiler/Runner Adapter
  └─ Result Publisher
```

推荐依赖：`spring-boot-starter-web`、`spring-boot-starter-security`、`mybatis-plus-spring-boot3-starter`、`spring-boot-starter-validation`、`spring-boot-starter-amqp`、`spring-data-redis`、`spring-ai`、Flyway、MySQL Connector/J、Testcontainers、springdoc-openapi。

判题任务通过 RabbitMQ 的持久化队列传递，消息仅保存 `submissionId`、语言和题目版本。Worker 从 MySQL/对象存储按权限获取源代码和测试点，执行后发布结果事件；API 服务更新提交状态并用 SSE 通知浏览器。

### 判题隔离

- 每次提交生成不可变 `submission_id`，Worker 从队列消费。
- 容器无网络、只读根文件系统、限制 CPU/内存/进程数和运行时长。
- 测试数据与题面分离；Agent、前端和普通 API 不可访问隐藏测试数据。
- 判题结果由签名事件写回，前端只消费服务端结果。

### Agent 安全边界

- Agent 只能通过白名单工具获取题面、用户代码、公开编译输出和已脱敏的判题摘要。
- 系统提示词禁止输出隐藏测试、完整标准答案（除非课程管理员明确开启）。
- 输出经过敏感信息、代码泄漏和提示注入检测；保留 prompt、tool call、model、版本和响应审计日志。
- 失败时降级为固定规则提示，不阻塞提交和判题。

## 6. 数据模型（核心表）

- `users(id, email, display_name, role, created_at)`
- `problems(id, slug, title, difficulty, statement_md, constraints_md, status, version)`
- `problem_tags(problem_id, tag_id)` / `tags(id, name, parent_id)`
- `test_cases(id, problem_version, visibility, input_ref, output_ref, weight)`
- `submissions(id, user_id, problem_id, language, source_ref, status, score, runtime_ms, memory_kb, created_at)`
- `submission_cases(submission_id, case_id, verdict, runtime_ms, memory_kb)`
- `agent_sessions(id, user_id, problem_id, submission_id, mode, model, created_at)`
- `agent_messages(id, session_id, role, content, tool_name, policy_result, created_at)`
- `learning_events(id, user_id, submission_id, event_type, tags_json, created_at)`
- `study_plans(id, user_id, period_start, period_end, goal, status)`

源代码、测试输入输出和大对象放对象存储，数据库只保存引用和哈希。

### MySQL 设计约定

- 使用 MySQL 8.0、`utf8mb4` 字符集和 `utf8mb4_0900_ai_ci` 排序规则；主键使用 `BIGINT` 或 UUIDv7，统一由应用生成。
- 高频查询索引：`submissions(user_id, problem_id, created_at DESC)`、`submissions(status, created_at)`、`learning_events(user_id, created_at DESC)`、`agent_messages(session_id, created_at)`。
- 题目状态、提交状态和语言使用 `VARCHAR` + 应用枚举，避免数据库 `ENUM` 给版本演进带来阻力。
- 提交创建与 RabbitMQ 发送采用 Outbox 表，在同一 MySQL 事务中写入 `submissions` 和 `outbox_events`；后台发布器负责投递与重试，避免“已提交但未入队”。
- 生产连接使用 HikariCP；慢查询阈值、连接池上限和 `EXPLAIN ANALYZE` 纳入上线检查。Redis 不作为提交结果的事实来源。

## 7. API 草案

```text
GET  /api/problems?difficulty=&tag=&status=
GET  /api/problems/:slug
POST /api/submissions                 # 创建提交，返回 submission_id
GET  /api/submissions/:id             # 查询结果
GET  /api/submissions/:id/events      # SSE：排队、编译、测试、完成
POST /api/agent/sessions
POST /api/agent/sessions/:id/messages  # 流式返回 SSE
GET  /api/me/learning/summary
GET  /api/me/study-plan
```

提交请求必须包含 `problem_version`、`language`、`source_code`；服务端校验版本、长度和语言白名单，并使用幂等键避免重复提交。

### Spring Boot API 约定

- Controller 仅负责 DTO 校验、鉴权和响应；业务放入 Application Service，Mapper 只负责数据读写，DO/DTO/VO 必须分层且不直接暴露。
- 使用 `@Valid`、统一 `ProblemDetail` 错误响应和全局异常处理器。
- 认证采用短期 Access Token + 可轮换 Refresh Token；管理员接口使用 `ROLE_ADMIN`。
- Agent 流通过 `SseEmitter` 或 WebFlux `Flux<ServerSentEvent<?>>` 输出；其请求和响应均关联 `traceId` 与 `agentSessionId`。
- 数据库迁移全部通过 Flyway；Mapper 不执行建表或结构变更，禁止在应用启动时自动修改表结构。

## 8. 多 Agent 设计

采用 **Supervisor + 专职 Agent** 的分层协作模式。Supervisor 不直接给出编程建议，而是识别用户意图、选择协作者、管理上下文预算并汇总最终结果。每个 Agent 具备独立系统提示、允许工具、输入 DTO 和结构化输出契约；它们不是同一个 Agent 的提示词模式。

```text
用户请求 / 提交结果
        │
        ▼
Supervisor Agent（路由、上下文裁剪、预算、终止条件）
  ├─ Tutor Agent        题意解释、苏格拉底式分级提示
  ├─ Debugger Agent     编译错误、失败摘要、代码缺陷定位
  ├─ Reviewer Agent     正确性、边界、复杂度、可读性审查
  ├─ Learning Agent     知识点掌握度、错题归因、训练计划
  └─ Safety Agent       提示注入、答案泄露、策略和输出审核
        │
        ▼
Finalizer（合并可用结论，生成用户可见的流式回答）
```

### 协作规则

1. Supervisor 根据 `intent`、是否已有提交、是否请求计划来路由。普通问答仅调用 Tutor；提交失败优先调用 Debugger，并并行调用 Reviewer；学习页请求只调用 Learning。
2. Safety Agent 在外部内容进入上下文前检查提示注入，并在 Finalizer 输出前审核。拒绝或降级由 Safety 结论决定，不能由其他 Agent 覆盖。
3. 每轮最多调用 3 个业务 Agent；默认只允许一次 Agent-to-Agent 追问，防止循环、成本失控和响应变慢。
4. 专职 Agent 只接收完成任务所需的最小上下文，不能彼此直接读取完整会话；Supervisor 传递经过脱敏的 `AgentTask`。
5. Agent 间共享的是结构化结论，不共享隐藏测试、标准答案或原始内部推理。Finalizer 只能引用经 Safety 审核的 `finding` 和 `hint`。

### Agent 职责与工具权限

| Agent | 输入 | 可调用工具 | 输出 |
| --- | --- | --- | --- |
| Supervisor | 用户请求、页面状态、提交摘要 | 路由、会话摘要 | `AgentPlan` |
| Tutor | 题面、公开示例、用户问题、提示等级 | 题面查询 | 分级 `Hint`、澄清问题 |
| Debugger | 用户代码、编译日志、脱敏失败摘要 | 代码解析、公开结果查询 | 行级 `Finding`、复现建议 |
| Reviewer | 用户代码、题目约束 | 代码解析、复杂度估算 | 正确性风险、复杂度结论 |
| Learning | 用户画像、提交/错题聚合 | 学习数据查询 | 知识点归因、训练项 |
| Safety | 所有待入/待出内容 | 策略检查、敏感信息检测 | `allow` / `redact` / `block` |

模型工具层只提供只读的题面、用户代码、公开样例、编译日志、脱敏判题摘要和用户本人学习数据。任何 Agent 都没有隐藏测试、标准答案、数据库直连、文件系统、Shell 或网络工具权限。

### Agent 间消息与响应契约

Agent 内部先生成结构化结果，再由 Finalizer 渲染自然语言：

```json
{
  "agent": "debugger",
  "task_id": "agt_task_01",
  "confidence": 0.86,
  "findings": [{"type": "boundary", "line": 14, "severity": "high"}],
  "hint_level": 1,
  "next_action": "ask_user_to_test_empty_input",
  "safety_status": "pending"
}
```

`AgentPlan`、`AgentTask`、`AgentResult` 与 `SafetyDecision` 以 Java record 定义，并以 JSON 写入审计表。这样可以做前端高亮、质量评估、重试、审计和后续模型替换，避免把模型文本直接当作业务事实。

## 9. 前端信息架构

- **首页**：今日训练、继续做题、近期错误模式。
- **题库**：搜索、筛选、列表/收藏/完成状态。
- **题目页**：左侧题面，中央编辑器，右侧可折叠 Agent 面板；底部固定提交状态与测试结果。
- **学习页**：知识点热力图、提交趋势、错题本、周计划。
- **管理页**：题目版本、测试点、发布审核、Agent 使用审计。

关键交互要求：Agent 面板不遮挡编辑器；提示级别由用户主动升级；提交过程可取消但不能伪造结果；移动端先保证读题、提交和查看结果。

详细页面布局、工作台三栏交互、响应式规则和多 Agent 面板状态见 [前端页面设计](frontend-design.md)。

## 10. 质量指标与验收

### OJ 指标

- 判题结果一致性 100%（同版本、同代码、同语言环境）。
- P95 提交结果返回时间：简单题 ≤ 8 秒，队列繁忙时可见排队状态。
- 沙箱逃逸、跨题数据读取、隐藏用例泄露：0 容忍。

### Agent 指标

- 首次响应 P95 ≤ 5 秒，流式首 token ≤ 2 秒；调用多个 Agent 时，Supervisor 在 1 秒内先返回处理状态。
- 提示有帮助率、用户采纳率、完整答案泄露率、幻觉举报率和路由正确率纳入离线评测。
- 为路由、每个专职 Agent 和最终汇总各准备 50 个金标准案例，发布前做回归评测。

### MVP 验收

新用户可在 3 分钟内注册、打开一道题、提交代码、看到判题结果，并在不刷新页面的情况下获得一次可解释且不泄露答案的提示。

## 11. 重新规划的开发阶段

### 总体策略

第一版只解决一条主路径：**用户用 Java 21 完成一道题的读题、编码、提交、判题、受控求助和复盘**。先使判题结果可信、可追溯，再增加 Agent 体验；不能让模型调用、比赛功能或多语言支持阻塞 OJ 核心链路。

赛制固定为 LeetCode 式练习制：每题独立、多次提交、全部测试点通过即 `Accepted`，不引入罚时、封榜和排行榜。

| 阶段 | 时间 | 目标 | 交付 | 退出条件 |
| --- | --- | --- | --- | --- |
| 0. 工程基线 | 第 1 周 | 让团队可稳定开发 | Maven 多模块、Spring Boot 3、MySQL、Redis、RabbitMQ、Docker Compose、Flyway、JWT/RBAC、OpenAPI | 新环境可一键启动；登录和受保护 API 可用；迁移可重复执行 |
| 1. 最小 OJ | 第 2-3 周 | 建立可信的提交和判题闭环 | 题目/题目版本、Java 21 模板、提交 API、提交历史、Judge Worker、持久化队列、Docker/gVisor 沙箱 | 至少 10 道题可稳定判题；相同代码/版本的结果一致；隐藏测试不可经 API 读取 |
| 2. 做题工作台 | 第 4 周 | 完成用户可用的前端主路径 | 题库筛选、题面、Monaco、运行公开样例、SSE 判题状态、结果与提交详情 | 用户无需刷新即可读题、运行、提交并看到 verdict、耗时和内存 |
| 3. 受控 Agent MVP | 第 5 周 | 让 Agent 辅助而不干扰判题 | Supervisor、Tutor、Debugger、Safety；分级提示、编译错误解释、脱敏上下文、审计日志 | Agent 不能访问隐藏测试/标准答案；失败不影响提交；首个流式响应满足性能目标 |
| 4. 学习闭环 | 第 6 周 | 将提交转化为下一步训练 | Reviewer、Learning Agent、错题本、标签掌握度、训练计划 v1 | 一次失败能生成错误归因、复盘建议和下一道推荐题 |
| 5. 管理与可靠性 | 第 7 周 | 使系统可运营 | 题目/测试点/版本管理、Outbox 重试、限流、监控、告警、沙箱和权限压测 | 可追溯题目发布与判题事件；队列故障可恢复；安全测试通过 |
| 6. 验收与灰度 | 第 8 周 | 达到 MVP 上线标准 | 金标准 Agent 评测、端到端回归、性能基线、灰度开关与反馈闭环 | 100 名种子用户可完成核心路径；关键指标和回滚方案就绪 |

### 每阶段的范围控制

- 阶段 1 仅支持 Java 21。Python 和 C++ 在 Java Judge、资源限制和错误归类稳定后再添加。
- 阶段 2 使用当前的首页与三栏工作台原型，不等待学习中心、比赛页或社区页面。
- 阶段 3 首发仅启用 Tutor、Debugger、Safety；Reviewer 和 Learning 在有真实提交数据后加入，避免空洞推荐。
- 任何阶段都不提前实现比赛、排行榜、公开题解、自动出题或服务拆分。

### 并行边界

前端可从阶段 1 开始以 Mock API 并行制作题库和工作台；Judge Worker 与 `submission` 模块同步开发，但沙箱镜像、队列事件与数据库状态机必须先约定契约。Agent 开发只能使用已经完成的题面、提交和脱敏结果接口，禁止直接连 MySQL 或判题数据目录。

### 首发后顺序

先扩展 Python/C++，再增加 OI 分组计分；当稳定用户量、判题并发或 Agent 流量证明模块化单体成为瓶颈时，再拆分 `agent-service`。比赛和排行榜最后评估，默认不进入首发路线图。

## 11.1 Java 工程结构

```text
backend/
  pom.xml                         # Maven 父工程
  codeagent-oj-server/
    src/main/java/com/ojagent/
      auth/ problem/ submission/ learning/ agent/ infra/ api/
    src/main/resources/
      application.yml
      db/migration/
  oj-judge-worker/
    src/main/java/com/ojagent/judge/
  docker-compose.yml
frontend/
  src/
```

Maven 多模块便于共享 DTO、队列事件和基础设施配置；`judge-worker` 必须采用独立镜像、独立运行账号与最小 MySQL 权限，不能直接调用用户侧 API。

## 12. 主要风险与决策

- **模型泄露答案**：分级提示、结构化响应、输出审核和管理员策略开关。
- **判题成本过高**：按语言/题目配置资源上限，队列限流，重复代码结果短期缓存。
- **低质量建议削弱信任**：显示依据和置信度；没有足够上下文时明确说“不确定”。
- **题目版权与数据安全**：题面、测试点、模型日志分级权限，生产数据脱敏。
- **范围失控**：先完成“做题闭环 + 可控提示 + 复盘”，社区和对战后置。

## 13. 下一步

1. 先确定首批 100 道题、支持语言（建议 Python/Java/C++）和模型供应商。
2. 用 10 道题建立 Judge 沙箱和 Agent 金标准评测集。
3. 按 API 契约并行开发题目页与提交服务，完成最小端到端闭环后再扩展学习计划。
