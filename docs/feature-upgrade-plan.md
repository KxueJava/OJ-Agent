# 功能补齐计划（阶段 1–4）执行记录

本文件记录「把半成品/名不副实的地方补齐」这轮改造的实际执行情况、验证方式与已知取舍。
每一阶段都遵循同一流程：**先实现 → 再跑可执行验证（编译 / 单测 / 端到端验收）→ 交由确认后才进入下一阶段**。

## 一、状态总览

| 阶段 | 内容 | 状态 | 关键验证证据 |
|---|---|---|---|
| 1 | 提交记录页 + 提交详情页（含对象级越权校验） | ✅ 已验证 | 26 单测绿、`tsc` exit 0；A 的列表 total=1、B 的列表 **total=0**、B 访问 A 详情 **404**、A 访问自己 **200**；`diagnosed` 标记由 False → True |
| 2 | 首页 AGENT COACH 接真实数据 + 岛屿数量用例对齐 + 设置页 | ✅ 已验证 | V23 应用后公开用例与题面示例**逐字一致**（4 行网格→3），旧 3 行网格降级为隐藏用例；真实 Agent 问答 `route=Tutor safety=PASSED content=671字 trace=Supervisor → Tutor → Safety → Finalizer` |
| 3 | 重判 rejudge + 判题阶段计时与失败分类 | ✅ 已验证 | V24 应用；提交后 `compileMs=1252 runtimeMs=2274 failureKind=USER`；重判后 `status=WA compileMs=1138 rejudgeCount=1`；公开用例行数重判前后**都是 1**（不重复累加）；B 重判 A → 400；普通用户调管理员接口 → 403 |
| 4 | 队列治理（死信/重试上限/积压告警）+ 提交限流 | ✅ 已验证 | V25 应用；连续 9 次提交 #1–#6 → 200、#7–#9 → **429**；`pending=0 published=177 dead=1 deadEvents=1`；死信重投 `requeued=1` → `dead=0`；日志出现 `OUTBOX_DEAD_TOTAL dead=1`；正常参数下 `OUTBOX_BACKLOG` 告警数 0 |
| 5 | P5 评估回归（金标准集与指标） | ⏸ 未开始 | 待确认后开始 |
| 6 | CI（GitHub Actions）+ E2E 测试 | ⏸ 未开始 | 待确认后开始 |

单测规模：服务端 25 + worker 4（含阶段 3 的失败分类 3 个、阶段 4 的限流 5 个）。

## 二、每阶段做了什么

### 阶段 1：提交记录与详情
- 新增 `GET /api/submissions?page&size&status&slug`：SQL 恒带 `user_id=?`，支持分页/状态筛选，并返回 `diagnosed`（该提交是否有自动诊断）。
- `GET /api/submissions/{id}` 的 `Detail` 增加 `language` / `sourceCode` / `slug`。
- 新增 `/submissions`（列表 + 状态筛选 + 分页）与 `/submissions/[id]`（判题结论、指标、公开用例、自动诊断 + 多 Agent trace、只读代码、重判按钮）。
- 入口接线：首页侧栏「提交记录」（原先点击只弹 toast「将在阶段 3 接入」）、题库页导航、工作台结果区「查看提交详情与诊断 →」。

### 阶段 2：真实数据与设置
- 首页 AGENT COACH 卡片改为真实数据：优先展示最近一次失败提交的**真实多 Agent 诊断**（真实 findings + trace + 跳转），否则回落规则推荐并如实标注 `Learning（规则推荐）`；删除了硬编码的 `Supervisor → Learning Agent` 与假回复。
- 提示 1/2/3 与提问框接真实 `POST /api/agent/ask`（带当前推荐题目的上下文）。
- **V23** 对齐岛屿数量：公开用例改成题面示例本身，原公开用例降级为隐藏用例（覆盖不缩水）。
- 新增 `/settings` + `lib/preferences.ts`（默认语言、编辑器字号、提交后自动诊断、桌面宠物），并接线到工作台（语言/字号/诊断开关）与全局 `template.tsx`（宠物开关）。

### 阶段 3：重判与判题可观测
- **V24**：`submissions` 增 `compile_ms` / `failure_kind` / `rejudge_count`。
- worker：编译与运行分开计时；失败归类 `USER`（用户代码）/ `INFRA`（沙盒不可用）/ `INTERNAL`（worker 异常）—— 以前三者都写成 `RE("判题沙盒执行异常")`，界面上看起来像用户代码错；文案同时改清楚（如「运行超时（超过时限）」「运行期错误（程序非正常退出）」）。
- 服务端：`POST /api/submissions/{id}/rejudge`（仅本人）、`POST /api/admin/submissions/rejudge`（ADMIN 按题目版本批量，上限 500）。重判复用现有 outbox → MQ → worker 链路（**worker 零改动**），先清掉旧判题用例与旧诊断再重新投递。
- 详情页展示编译耗时、重判次数，`INFRA`/`INTERNAL` 时给出「不是你代码的错误」提示。

### 阶段 4：队列治理与限流
- **V25**：outbox 增 `dead_lettered_at`。
- 投递不再无限重试：失败按 `3×attempts` 秒退避（上限 60s），超过 `max-attempts`（默认 8）落 `DEAD` 并打 `OUTBOX_DEAD` 错误日志。
- 看门狗 `OutboxMonitor`：默认每 30s 检查积压（待投递 ≥20 条或最老待投递 ≥60s）与死信增长，打出可 grep 的 `OUTBOX_BACKLOG` / `OUTBOX_DEAD_TOTAL`。
- 管理端：`GET /api/admin/judge/queue`（积压/死信/最老等待 + 死信清单）、`POST /api/admin/judge/outbox/{id}/requeue`（重投）。
- 限流 `SubmissionRateLimiter`：默认每用户 6 次/分、60 次/时，超出返回 **429** 并说明原因；另有队列保护（待投递 ≥500 → 503）。被拒的请求不计数。

## 三、验证脚本（可复现）

| 脚本 | 作用 |
|---|---|
| `tools/stage1-verify.ps1` | 提交记录/详情接口 + 对象级越权（B 看不到 A）+ 筛选与分页 |
| `tools/stage2-verify.ps1` | 应用 V23、核对示例与用例一致、首页卡片依赖接口、真实 Agent 问答 |
| `tools/stage3-verify.ps1` | 计时/分类字段、重判幂等（用例不翻倍）、越权 400、管理员 403 |
| `tools/stage4-verify.ps1` | 限流 429、管理端队列可见性、死信重投、告警日志 |

辅助工具：`tools/FlywayRepair.java`（清理失败的迁移记录）、`tools/JudgeAdmin.java`（合成死信 / 提升 ADMIN / 统计）、`tools/Stage2Check.java`（Flyway 版本与用例核对）。

## 四、已知取舍与注意事项

1. **失败迁移必须先清理**：MySQL 下 Flyway 会把失败的迁移写进 `flyway_schema_history(success=0)`，之后每次启动都会被「Detected failed migration」拒绝。用 `tools/FlywayRepair.java <version>` 删掉失败行再重启。
2. **迁移里不要重建已有索引**：`outbox_events` 在 V4 已有 `idx_outbox_pending (status, available_at, created_at)`；重复建同名索引会因 `Duplicate key name` 让整个 ALTER 回滚。
3. **tools 下的 .ps1 需要 UTF-8 BOM**：PowerShell 5.1 对无 BOM 的 UTF-8 脚本按 GBK 解析，中文会吃掉引号导致语法错误。
4. **死信在 outbox 层而不是 RabbitMQ DLX**：outbox 才是判题投递的可靠缓冲，RabbitMQ 队列只是传输；给已存在的队列加 `x-dead-letter-exchange` 需要删队列重建，属于有风险操作，暂不做。
5. **节流/限流是进程内的**：多实例部署时需要下沉到 Redis 或 DB，否则每个实例各限一份。
6. **管理员批量重判只有 API 入口**：没有做管理端页面按钮。
7. **失败分类的 `INFRA` 分支用单测覆盖**：端到端复现需要真的让沙箱或 worker 出问题（例如停掉 RabbitMQ 或换成不存在的镜像），会污染正在使用的环境，因此只在单测中断言，未做端到端演练。
