# 从「OJ + AI 助教」到「Agent 版 OJ」改造方案

> 状态：**P0、P1 已实现并验证**，P2–P5 待做。前置记忆层见 [agent-memory-plan.md](agent-memory-plan.md)。
> 原则来源：[project-plan.md](project-plan.md) §2「判题结果由 OJ 决定，学习建议由 Agent 生成」、§8「多 Agent 设计」。

## 实施状态

| 阶段 | 状态 | 验证证据 |
|---|---|---|
| P0 前置 | ✅ 已完成 | `mvn test` 13/13；Flyway V17 落库；真模型回答 878 字；对话记忆 `memory_hit=True`；**修掉"空 api-key 导致应用启动失败"**；两个 `sourceCode` 取值 bug 已修 |
| P1 工具层 | ✅ 已完成 | 3 个只读工具类 + `ToolContext` 租户隔离 + `agent_tool_calls` 审计（V18 已落库）；单测断言"隐藏用例的输入/期望输出永不进入查询""查询恒带用户边界""无租户不查库"；运行时验收：Agent 自主调工具并引用真实提交号 `#385458161072140160`，报出"公开用例 1 AC / 隐藏用例 0-1"，且主动声明看不到隐藏数据 |
| P2 闭环 | ✅ 已完成 | V19 `agent_findings` + `AutoDiagnosisService`（幂等/节流/超时/降级）+ `GET /api/agent/submissions/{id}/diagnosis` + 工作台诊断卡片；运行时验收：交 WA **不点任何按钮，4 秒**出诊断（`safety=PASSED`，模型自主连调 4 个工具）；假 key 时同样 4 秒落规则化建议（`safety=FALLBACK`）；`tsc --noEmit` 通过 |
| P3 结构化 + 流式 + trace | ✅ 已完成 | V20（`findings_json`/`output_review`/`trace_json`）+ `Diagnosis` record + `OutputSafety`（5 个单测）+ `.entity()` 结构化输出 + `.stream()` 真流式 + 真实 trace；运行时验收：`findings_json` 返回 `[{file:Main.java,line:1,severity,message,suggestion}×3]`、`output_review=PASSED`、trace=`Supervisor -> Debugger -> getSubmissionHistory(1ms) -> getLatestPublicRun(1ms) -> getSubmissionDetail(3ms) -> Safety -> Finalizer`、**首 token 1056ms**（< 2s 门槛）；前端 finding 可点击跳转行号，`tsc --noEmit` 通过 |
| P4 多 Agent | ✅ 已完成 | V21（`trace_json`）+ `AgentOrchestrator`（Supervisor 规划 → 专职 Agent 并行 → Safety → Finalizer）+ 3 个规划单测；运行时真实轨迹：`Supervisor(plan=Debugger+Reviewer) → Debugger(ok,3809ms) ∥ Reviewer(ok,3289ms) → Finalizer(ok,1544ms) → Safety(PASSED)`，并行证据是"3.8s+3.3s 而非串行 7.1s"；后端 21 个测试全绿、流式首 token 932ms 无回归 |
| P5 评估与可观测 | ⏸ 按用户决定暂缓 | 计划交付：金标准场景集（含 AC 负对照）+ 命中率/延迟/误触发/并行证据的回归跑分脚本 + 指标口径文档；这是"能跑"到"知道它可靠"的分界，改了 prompt 或换模型后靠它判断好坏 |

> **四道门槛已全部达成**：① 有工具 ② 有闭环 ③ 有结构化产出 ④ 有自主循环（多 Agent 编排）。

### 已知运维注意点

1. **节流口径（2026-10-01 修正）**：最初按本文原方案实现为"同一用户+同一题 5 分钟只自动诊断一次"，结果把"改一版 → 提交 → 看诊断"的正循环堵死了 —— 实测中用户连续 4 次 WA 提交全部被静默跳过（`agent_findings` 14 点整点 0 条），前端轮询 30 秒后默默放弃，用户只看到"没反应"。现改为：**短冷却 20 秒**（只吸收连点/重复提交）+ **每用户每小时 20 次额度**（真正的成本闸），并且查询接口在无诊断时返回 `status=SKIPPED/PENDING/NONE` 与 `reason`，把"为什么没有诊断"明确告诉前端。规则抽成 `DiagnosisThrottle` 并有 5 个单测（冷却、跨题不互斥、额度、滚动小时恢复、按用户隔离）。
2. **多实例**：节流仍是进程内的。同时跑多个实例时唯一键能防住重复行，但"先写入的实例说了算" —— 实测中 8080 上的旧版本实例先一步诊断了新提交，导致新版本代码里的 `findings_json` 没被写入。单实例无此问题；多实例部署需把节流下沉到 DB。
3. **连接回收**：日志里出现过 Hikari `No operations allowed after connection closed`，已在 `spring.datasource.hikari` 加 `max-lifetime: 600000` + `keepalive-time: 300000` 主动回收。

## 0. 先定义"算不算 Agent 版"

四条门槛，缺一不可。当前仓库 **0/4**：

| # | 门槛 | 现在 | 本方案对应阶段 |
|---|---|---|---|
| 1 | **有工具**：Agent 自己按需取上下文，而不是让用户贴代码 | ❌ 没有 `@Tool` | P1 |
| 2 | **有闭环**：提交失败 → 自动诊断 → 定位到行 → 建议 → 复测 | ❌ 全靠用户点按钮 | P2 |
| 3 | **有结构化产出**：诊断是可被程序消费的 record/表数据 | ❌ 只有自然语言 | P3 |
| 4 | **有自主循环**：一次请求内多步（取数 → 复现 → 定位 → 方案） | ❌ 一次生成 | P4 |

判定标准即验收标准。

## 1. 目标形态

```text
提交 ── outbox ── RabbitMQ ──► Judge Worker（已有：Docker 沙箱）──► submissions / submission_cases
                                  │
                                  │ 新增 SUBMISSION_FINISHED 事件
                                  ▼
        Agent Orchestrator（仍留在 codeagent-oj-server 内，包边界隔离）
          ├─ Trigger     事件驱动（终态自动触发） + 用户主动提问（/api/agent/*）
          ├─ Supervisor  P4：规划调哪些专职 Agent、预算与终止
          ├─ Tools       P1：@Tool 只读白名单，租户从 ToolContext 取
          ├─ Agents      Debugger / Reviewer / Tutor / Learning（各自 prompt + 输出契约）
          ├─ Safety      输入侧（已有 AgentPolicy）+ P3 输出侧独立审核
          ├─ Findings    P3：结构化落库 agent_findings，前端可直接高亮
          └─ Memory      已有：ChatMemory + agent_messages（V17）
                                  │
前端（工作台）: verdict + 诊断卡片（file:line 可跳转）+ 流式建议
```

**复用已有基建，不另起炉灶**：outbox 表与 `@Scheduled` 发布器、`/api/submissions/{id}/events` 的 SSE、`SubmissionService.detail` 的 `visibility='PUBLIC'` 过滤、`agent_sessions/messages/audits` 审计口径。

## 2. 分阶段改造（每阶段可独立上线、可回滚）

### P0 前置（阻塞一切）：让 Spring AI 真正跑起来

`%USERPROFILE%\.m2\repository\org\springframework\ai` 目前不存在 → 加依赖后**从未成功编译/启动过**。P0 必须做完，否则后续全部是纸面工作。

1. `mvn -q -DskipTests compile`；起服务，确认 `ChatClient` bean 与 `spring.ai.openai.*` 绑定，**确认空 `api-key` 是否导致启动失败**（若失败，现有"规则兜底"保护形同虚设）。
2. 配置收敛：删掉 `app.agent.deepseek.*` 冗余块，`DeepSeekClient.configured()` 改判 Spring AI 侧配置。
3. 修两个取值 bug（不修则 `sourceCode` 恒为空，工具层做完也只能泛泛而谈）：
   - `desktop-pet.tsx`：`problem.templateCode` → `javaTemplate`
   - `desktop-pet.tsx` / `agent/[slug]/page.tsx`：草稿 key `...:java21` → `...:JAVA_21`
   - `template.tsx`：`<DesktopPet />` 无参渲染 → 由工作台把 `problemVersionId` / 当前 `code` / `verdict` 传下去

**验收**：`/api/agent/ask` 能拿到真实模型回答，且 `sourceCode` 非空。

### P1 工具层：把"用户贴代码"变成"我自己取"

新增 `agent/tools/` 包，用 Spring AI 1.0 的 `@Tool`（`org.springframework.ai.tool.annotation.Tool`）暴露**只读白名单**工具：

| 工具 | 返回 | 数据来源 |
|---|---|---|
| `getProblemBrief()` | slug/title/statement/constraints/tags | `problems`、`problem_versions`、`problem_tags` |
| `listExamples()` | 公开样例 | `examples` |
| `getSubmissionHistory(limit)` | 该用户在此题的提交摘要（id、status、runtime、时间） | `submissions` |
| `getSubmissionDetail(id)` | 自己某次提交的 verdict + **仅 PUBLIC 用例**的 outputSummary | `submission_cases` join `test_cases`（沿用 `visibility='PUBLIC'` 过滤） |
| `getCompileLog(id)` | CE 的编译输出（若已落库，没有则返回"不可用"） | `submissions.verdict_message` / 新增列 |
| `getLearningContext()` | 标签掌握度、错题、今日计划 | `learning_events`、`mistake_books`、`study_plans` |
| `listPublicRuns(limit)` | 自己的公开样例运行记录 | `public_runs`、`public_run_cases` |

**安全红线（写进代码 + 单测，不是靠 prompt）**

1. 工具集**全部只读**：不提供 SQL、文件、Shell、网络、提交/修改类工具。
2. **租户只从 `ToolContext` 取**（由 JWT 派生）：`userId` / `problemVersion` **绝不出现在模型可见的工具参数里**，否则模型可被诱导越权读他人数据。
3. 隐藏用例永不返回 input/expected；只允许返回"第 N 个用例未通过"这类计数与序号。
4. 返回值统一截断（如 2000 字符）并标注截断。
5. 每次调用写 `agent_tool_calls` → 审计可回放"模型当时拿到了什么"。

接线方式：

```java
chatClient.prompt().system(systemPrompt).user(userPrompt)
        .tools(problemTools, submissionTools, learningTools)
        .toolContext(Map.of("userId", user, "problemVersion", version))
        .call().content();
```

**验收**：问"我上次为什么 WA"，Agent 自己调 `getSubmissionHistory` + `getSubmissionDetail`，回答里出现具体 verdict 与用例序号，而不是要求用户贴代码。

### P2 闭环：提交失败自动诊断

**触发方式（推荐 v1 走简单版）**：API 侧定时任务扫描"已到终态且尚无 finding"的提交（与现有 `publishOutbox` 的轮询风格一致）；规模化后再改成 JudgeWorker 在 `finish(...)` 后写 `outbox_events(event_type='SUBMISSION_FINISHED')` + API 侧 `@RabbitListener`。

**必须有的约束**

- 只对**非 AC** 终态触发；同一 `(submission_id, kind)` 幂等（唯一键），已存在直接跳过。
- 频率与成本：同一用户同一题 5 分钟内只自动诊断一次；每轮最多一次模型调用（P4 后才允许多 Agent）。
- 隔离：独立线程池 + 15s 超时 + 全局开关 `app.agent.auto-diagnose.enabled`；**任何失败只记日志，绝不阻塞或修改判题结果**（延续 §2 职责隔离）。
- 降级：模型不可用时写一条 `kind='DIAGNOSIS', safety_status='FALLBACK'` 的规则建议，而不是留空。

**验收**：提交一份 WA 代码，**不点任何按钮**，10 秒内工作台自动出现诊断卡片。

### P3 结构化输出 + 真流式 + 真实 trace

1. **结构化**：定义 `Diagnosis { summary, List<Finding> findings }`、`Finding { file, line, severity, message, suggestion }`；用 `chatClient...call().entity(Diagnosis.class)`（Spring AI 自带 `BeanOutputConverter`）拿到对象后落 `agent_findings.findings_json`。前端不再解析自然语言，直接渲染并可跳到 `文件:行号`。
2. **输出侧 Safety**：模型输出再过一道独立审核（可用更便宜/更严的配置），不合格降级为模板话术；`agent_audits` 增加 `output_review`。
3. **真流式**：`/api/agent/stream` 换成 `chatClient.prompt().stream().content()`，按 token 推 SSE；前端 `agent-panel.tsx` 改为读流（当前只调 `/ask`，伪流式接口没人用）。
4. **真实 trace**：把写死的 `List.of("Supervisor", route, "Safety", "Finalizer")` 换成真实轨迹（调了哪些工具、哪些 Agent、各步耗时），落 `agent_audits.trace_json`。

**验收**：首 token < 2s（文档 §9 已有指标）；finding 可直接高亮到行。

### P4 多 Agent 编排（P1–P3 稳定后再做）

- Supervisor 做真正的规划：产出 `AgentPlan`，决定调 Debugger / Reviewer / Learning 与否、并行度、终止条件。
- 专职 Agent 各自 system prompt + 输出契约 + **工具子集**（不再是"一个 prompt 扮演四个角色"）。
- Debugger ∥ Reviewer 并行（虚拟线程 / `CompletableFuture`），Safety 后置，Finalizer 只汇总经审核的 finding。
- 沿用文档 §8 的预算：每轮最多 3 个业务 Agent、最多一次 Agent-to-Agent 追问。

**验收**：文档 §8 已有的 50 条金标准用例回归通过率不低于单 Agent 基线。

### P5 评估与可观测

- 金标准集（每题 3–5 个诊断场景）进 CI；指标：路由准确率、工具调用成功率、诊断命中率、P95 延迟、token 成本。
- 管理端已有 `/api/admin/agent/audits`，扩展为可查工具调用与 finding。

## 3. 数据模型变更

```sql
-- V18__agent_findings.sql
CREATE TABLE agent_findings (
    id BIGINT NOT NULL PRIMARY KEY,
    submission_id BIGINT NULL,
    session_id BIGINT NULL,
    kind VARCHAR(24) NOT NULL,             -- DIAGNOSIS / REVIEW / HINT
    verdict VARCHAR(16) NULL,
    summary VARCHAR(512) NULL,
    findings_json MEDIUMTEXT NOT NULL,     -- file:line / severity / message / suggestion
    model VARCHAR(64) NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    latency_ms INT NULL,
    safety_status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_findings (submission_id, kind),
    INDEX idx_agent_findings_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_tool_calls (
    id BIGINT NOT NULL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    tool VARCHAR(48) NOT NULL,
    args_json VARCHAR(1024) NULL,
    result_chars INT NULL,
    ok TINYINT(1) NOT NULL,
    latency_ms INT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_agent_tool_calls_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE agent_audits
    ADD COLUMN trace_json MEDIUMTEXT NULL,
    ADD COLUMN output_review VARCHAR(16) NULL;
```

`outbox_events` 不改表，只新增 `event_type='SUBMISSION_FINISHED'`。

## 4. 代码结构（包边界）

```text
com.codeagentoj.server.agent
  ├─ AgentController            HTTP 入口（契约不变）
  ├─ AgentPolicy                输入侧规则（已有）
  ├─ DeepSeekClient             模型适配（已有，P3 加 entity/stream）
  ├─ AgentMessageRepository     记忆 = 审计同源（已有）
  ├─ AgentMemoryConfig          记忆窗口（已有）
  ├─ tools/
  │    ├─ ProblemTools
  │    ├─ SubmissionTools
  │    ├─ LearningTools
  │    └─ ToolContextHolder     租户上下文（JWT → userId/problemVersion）
  ├─ DiagnosisService           P2：触发、幂等、超时、落库
  ├─ AgentOrchestrator          P4：Supervisor + 并行 + Finalizer
  └─ dto/                       Diagnosis / Finding / AgentPlan / AgentTask
```

**边界原则**：`agent` 包只做**只读查询**与写入自己的 `agent_*` 表；永不写 `submissions` / `problem_versions` / `test_cases`。判题结果只能由 OJ 产生。

## 5. 风险、红线与对策

| 风险 | 对策 |
|---|---|
| 隐藏测试泄漏 | 工具只走 `visibility='PUBLIC'`；单测断言任何入参下都不返回 hidden 的 input/expected |
| 模型越权读他人数据 | 租户只从 `ToolContext` 取，工具签名里不出现 userId/problemVersion |
| 提示注入驱动工具滥用 | 工具全部只读；输入侧保留 `AgentPolicy`；P3 加输出侧审核 |
| 自动诊断拖垮判题 | 独立线程池 + 超时 + 开关 + 只对非 AC 触发；失败不影响提交状态 |
| 成本失控 | 每轮最多 3 个 Agent；`(submission_id,kind)` 幂等缓存；prompt 里只放必要片段 |
| 模型不可用 | 保留规则兜底（`in_memory=0`，只审计），链路不中断 |
| token 预算 | 记忆已有字符预算（V17）；工具返回值截断；P3 起记录 token 用量 |

## 6. 里程碑与验收对照

| 阶段 | 交付 | 验收 | 预估 |
|---|---|---|---|
| P0 | 编译启动打通 + 配置收敛 + 取值 bug | 真模型回答且 sourceCode 非空 | 0.5 天 |
| P1 | 工具层 + `agent_tool_calls` + 单测 | "我上次为什么 WA" 能引用具体 verdict/用例 | 2–3 天 |
| P2 | `DiagnosisService` + 自动触发 + 诊断卡片 | 交 WA 不点按钮，10s 内出诊断 | 2–3 天 |
| P3 | 结构化 finding + 输出审核 + 真流式 + 真 trace | 首 token < 2s；finding 可跳转行 | 2–3 天 |
| P4 | Supervisor + 并行专职 Agent | 金标准集回归不劣于单 Agent | 4–5 天 |
| P5 | 金标准集 + 指标 + CI | 指标可查、回归进流水线 | 2 天 |

## 7. 本期明确不做

- 拆 `agent-service` 微服务（文档 §6：等流量证明模块化单体成为瓶颈再拆）。
- 向量库 / RAG（题库仅几十道，全文进 prompt 更便宜更准）。
- 比赛、榜单等非 Agent 功能。
- 让 Agent 参与判题（红线：判题结果只由 OJ 产生）。
