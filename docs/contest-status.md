# 竞赛功能实现状态（P1–P3）

配套文档：[contest-plan.md](contest-plan.md)（方案）、[../prototype/contest-acm.html](../prototype/contest-acm.html)（设计稿）。
最后更新：本会话内，竞赛功能实施到 P3。

## 一、阶段状态

| 阶段 | 内容 | 代码 | 验证 |
|---|---|---|---|
| **P1** | V26 建表 + 管理端 CRUD + 发布校验预检 | ✅ | ✅ **已端到端验证** |
| **P2** | 发布 / 取消 / 定榜 + 状态推进定时器 + 公告 | ✅ | ✅ **已端到端验证**（含 4 个负向用例的 HTTP 409 判定） |
| **P3** | 公开接口 + 竞赛列表页 / 详情页 + i18n | ✅ | ⚠️ **后端已端到端验证；前端页面只做了静态核对，缺编译验证** |
| — | 管理端 UI（`/admin` 竞赛分组） | ❌ 未做 | 方案里属 P1/P2 范围，目前只有接口，管理员只能用 API/脚本操作 |
| P4 | 报名 + 提交归属 `contest_id` + 实时榜单 + 冻结 | 后端 ✅ / 前端 ❌ | ⚠️ 未验证（脚本已备：`tools/contest-p4-verify.ps1`） |
| P5 | 定榜快照 + 竞赛内批量重判 | ❌ 未开始 | — |

## 二、已验证的证据（可直接复现）

| 检查 | 结果 |
|---|---|
| 后端单测 | 服务端 44 用例 + worker 4 用例，`BUILD SUCCESS` |
| 迁移 | V26 `contests/contest_problems/contest_participants` + `submissions.contest_id` + `admin_audit_log`；V27 `contest_announcements`；均应用到 v27 |
| P1 | 建草稿 → `DRAFT`；空题集发布校验 → `problems.empty`；加两题自动分配 A/B；重复加题 **409**；冻结≥时长 → `freeze.tooLong`；删 A 题后 B 重排为 A；普通用户调管理端 **403** |
| P2 | 空题集发布 → HTTP 200 且 `published=false`；选题后发布 → `SCHEDULED` + `publishedAt`；定时器 `CONTEST_STARTED/CONTEST_ENDED`；ENDED 可定榜、重复定榜 **409**、RUNNING 定榜 **409**；取消后重复取消 **409**；DRAFT 发公告 **409** |
| P3 后端 | DRAFT/CANCELLED 公开详情 **404**；SCHEDULED `problemsHidden=true`（标题隐藏）；ENDED 题目可见；公开列表排除 DRAFT/CANCELLED |
| 审计 | `admin_audit_log` 只记录合法动作，每种恰好一条（CREATE/ADD_PROBLEM/PUBLISH/ANNOUNCE/CANCEL/FINALIZE） |

验收脚本：`tools/contest-p1-verify.ps1`、`contest-p2-verify.ps1`、`contest-p3-verify.ps1`；
一键跑全部：`tools/contest-verify-all.ps1 -ApiKey <KEY>`（**纯 ASCII 编写**，规避 PowerShell 5.1 按 GBK 解析无 BOM 脚本的问题）。

## 三、过程中发现并修掉的真问题

1. **公开接口被安全配置拦成 401**：`SecurityConfig` 的 `permitAll` 未包含 `/api/contests/**`，"公开"接口实际要登录。只有端到端验收能发现（编译和单测都不会报）。
2. **V23/V25 两次迁移把失败记录留在 `flyway_schema_history`**：MySQL 非事务 DDL 下失败迁移会阻止后续启动，需用 `tools/FlywayRepair.java <version>` 清理。另：迁移里**不要重建已有索引**（`outbox_events` 的轮询索引在 V4 就有）。
3. **旧提交可重判**：V23 改了公开用例后，历史提交需要重判口径（已实现 `rejudge`，P5 会把它接到竞赛维度）。

## 四、当前阻塞与建议

**阻塞**：P3 前端的编译验证做不了 —— 本会话的命令提权被拒（`npx tsc --noEmit` 无法执行），用户也尚未提供运行结果。
**建议顺序**：① 跑一次 `npx tsc --noEmit` 让 P3 闭环 → ② 补管理端 UI（还 P1/P2 的欠账）→ ③ 做 P4（报名 + 提交归属 + 实时榜单 + 冻结）→ ④ P5 定榜快照与竞赛内重判。

**已知设计取舍**（做 P4 前需确认）：
- 竞赛期间**提交限流**需放宽（现在 6 次/分，建议按场次覆盖为 12 次/分）；
- 开赛瞬间的**沙箱并发**是否单独设上限（避免把普通判题挤掉）；
- 报名是否需要参赛密码 / 人数上限（字段已预留）；
- Rating/ELO 本期不做。
