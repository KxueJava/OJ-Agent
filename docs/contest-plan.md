# 竞赛功能方案（管理端发布竞赛）

线上判题系统的竞赛模块，核心是"管理员在管理端把一场比赛配置好并发布，选手在竞赛页报名参赛、看实时榜单"。
本文件只做设计，不含实现。配套设计稿：`prototype/contest-acm.html`。

## 一、目标与范围

**本期做**：ACM 赛制的公开赛（报名制）——管理员创建草稿、选题、设规则、发布；选手报名、在赛程内提交、看实时榜单；支持**榜单冻结**与**赛后定榜**。

**本期不做**（预留字段，不实现）：Rating/ELO 变化、组队赛、邀请制私有赛、IOI 赛制计分、题目部分分。

## 二、角色与权限

| 角色 | 能做 |
|---|---|
| ADMIN | 创建 / 编辑 / 选题 / 发布 / 取消 / 定榜 / 竞赛内批量重判 / 发布公告 |
| USER | 浏览已发布竞赛、报名、参赛、看公开榜单（不含冻结期数据） |

- 全部管理接口沿用 `@PreAuthorize("hasRole('ADMIN')")`（现有 `AdminController` 同一套）。
- **所有管理动作写审计**（谁在何时发布/取消/定榜/改了时间），便于赛后追责：新增 `admin_audit_log` 或复用 `agent_audits` 的写法。

## 三、数据模型（V26 迁移草案）

```sql
CREATE TABLE contests (
  id BIGINT PRIMARY KEY,
  slug VARCHAR(64) NOT NULL UNIQUE,
  title VARCHAR(128) NOT NULL,
  description_md TEXT,
  mode VARCHAR(16) NOT NULL DEFAULT 'ACM',        -- ACM | IOI（预留）
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',    -- 见状态机
  start_at DATETIME NOT NULL,
  end_at DATETIME NOT NULL,
  freeze_minutes INT NOT NULL DEFAULT 20,         -- 最后 N 分钟冻结榜单
  penalty_minutes INT NOT NULL DEFAULT 20,        -- 每次错误提交的罚时
  max_participants INT NULL,
  password_hash VARCHAR(100) NULL,                -- 可选参赛密码
  published_at DATETIME NULL,
  finalized_at DATETIME NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE contest_problems (
  id BIGINT PRIMARY KEY,
  contest_id BIGINT NOT NULL,
  problem_id BIGINT NOT NULL,
  problem_version_id BIGINT NOT NULL,   -- 关键：钉住版本，赛中改题不影响比赛
  label CHAR(1) NOT NULL,               -- A..Z
  score INT NOT NULL DEFAULT 100,
  display_order INT NOT NULL,
  UNIQUE KEY uk_contest_label (contest_id, label),
  UNIQUE KEY uk_contest_problem (contest_id, problem_id)
);

CREATE TABLE contest_participants (
  id BIGINT PRIMARY KEY,
  contest_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  registered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  started_at DATETIME NULL,             -- 首次进入比赛时间（用于"迟到参赛"提示）
  rank_final INT NULL, solved_final INT NULL, penalty_final INT NULL,  -- 定榜快照
  UNIQUE KEY uk_contest_user (contest_id, user_id)
);

-- 提交归属：提交时若处于赛程内且已报名，则打上 contest_id
ALTER TABLE submissions ADD COLUMN contest_id BIGINT NULL;
ALTER TABLE submissions ADD INDEX idx_submissions_contest (contest_id, user_id, created_at);
```

**为什么是"提交带 contest_id"而不是"中间表"**：榜单要能一条 SQL 从 `submissions` 现算，避免"提交表 + 竞赛提交表"双写不一致；`contest_id` 只是标签，判题链路完全不变。

## 四、状态机与守卫

```
DRAFT ──发布(校验)──> SCHEDULED ──到 start_at(定时器)──> RUNNING ──到 end_at──> ENDED ──定榜──> FINALIZED
  │                      │                                  │
  └────────取消───────────┴───────────取消(二次确认)─────────┘  → CANCELLED
```

**发布前置校验**（任一不满足就拒绝，且**逐条返回失败原因**，不能只说"发布失败"）：

1. 至少 1 道题；
2. `start_at < end_at`，且总时长 ≥ 5 分钟；
3. `freeze_minutes < 总时长`；`penalty_minutes ≥ 0`；
4. 每个 `problem_version_id` 必须 `status='PUBLISHED'`；
5. `slug` 唯一；同题不重复；label 连续（A、B、C…）；
6. 编辑赛程时间**仅允许在 `SCHEDULED`**；一旦 `RUNNING`，时间只能靠公告，不改数据。

**状态推进**用 `@Scheduled` 扫描（同 `OutboxMonitor` 模式），多实例下用**条件更新**避免重复推进：

```sql
UPDATE contests SET status='RUNNING' WHERE status='SCHEDULED' AND start_at<=NOW();
UPDATE contests SET status='ENDED'   WHERE status='RUNNING'   AND end_at<=NOW();
```

时间判定一律用 **DB 时间（NOW()）**，不用应用服务器时间，避免多实例/时区差异。

## 五、接口清单

**管理端（全部 ADMIN）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/contests?status=&page=` | 列表（草稿/已发布/进行中/已结束） |
| POST | `/api/admin/contests` | 创建草稿 |
| GET | `/api/admin/contests/{id}` | 详情 + **校验预检结果**（哪些项不满足） |
| PUT | `/api/admin/contests/{id}` | 改配置（DRAFT / SCHEDULED 可改） |
| POST | `/api/admin/contests/{id}/problems` | 加题（slug 或 problemVersion + label + score） |
| DELETE | `/api/admin/contests/{id}/problems/{problemId}` | 移除题目并重排 label |
| POST | `/api/admin/contests/{id}/publish` | 发布（返回校验清单） |
| POST | `/api/admin/contests/{id}/cancel` | 取消（二次确认） |
| POST | `/api/admin/contests/{id}/finalize` | 定榜（写 `*_final` 快照） |
| POST | `/api/admin/contests/{id}/rejudge` | 竞赛内批量重判（**复用阶段 3 已实现的 rejudge**） |
| GET | `/api/admin/contests/{id}/standings` | 管理视角榜单（**不受冻结限制**） |
| POST | `/api/admin/contests/{id}/announcements` | 发公告 |

**选手端（公开）**：`GET /api/contests`、`GET /api/contests/{slug}`、`POST /api/contests/{slug}/register`、`GET /api/contests/{slug}/standings`（冻结期内只回冻结前数据）。

## 六、榜单与冻结算法

- 只统计 `contest_id = 本场` 且 `created_at ∈ [start_at, end_at]` 的提交（**赛前/赛后提交自动排除**）。
- 每题：取首次 AC；罚时 `= (首次AC时刻 - start_at) + penalty_minutes × 该题首次 AC 之前的非 AC 提交次数`。
- 排序：`solved DESC, penalty ASC, 最后 AC 时刻 ASC, user_id ASC`（最后一项保证**确定性排序**，避免同分抖动）。
- 冻结：`NOW() ≥ end_at - freeze_minutes` 时，公开榜单只取 `created_at < freeze_at` 的提交；管理员视角不受限；`ENDED` 后自动解冻重算；`FINALIZED` 用快照（不再随数据变化）。
- 性能：榜单按 `contest_id` 全量计算即可（千级提交量），若并发高再用 Redis 缓存 30 秒。

## 七、管理端 UI（在现有 `/admin` 增加"竞赛"分组）

- **列表**：状态 / 时间 / 题目数 / 报名数 / 操作（编辑、发布、取消、定榜、看榜单）。
- **编辑（三步式）**：① 基本信息（名称、时间、赛制、罚时、冻结、可选密码、人数上限）② 选题（题库搜索 + 排序 + 自动分配 A..E + 每题分值）③ **预览与发布**：校验清单逐条红/绿 + 二次确认。
- **发布后**：转为只读视图，可发公告；`SCHEDULED` 阶段仍可改时间。
- 交互原则：**校验失败要列出具体原因**（"C 题引用的版本未发布"），不要一句"发布失败"。

## 八、复用与影响面

**直接复用**：题目版本（钉版本）、判题链路（不改 worker）、阶段 3 的 rejudge、ADMIN 权限与审计、i18n（新页面照 `lib/messages/*` catalog 模式）、竞赛页设计稿（`prototype/contest-acm.html`）。

**会动到的地方**：
1. `submissions` 加 `contest_id`（V26，向后兼容，旧数据为 NULL）；
2. 提交接口：在赛程内且已报名则写 `contest_id`；
3. **限流需要为竞赛放宽**（现在每用户 6 次/分对竞赛太紧）→ 竞赛期间按场次覆盖阈值（如 12 次/分），赛后恢复；
4. 新增 `/contests`、`/contests/[slug]` 与 `/admin/contests/*` 页面。

## 九、边界与风险

1. **赛中改题面/数据**：禁止（钉版本）。确有数据错误 → 发公告 + 用竞赛内 rejudge 重判。
2. **时钟**：统一 DB 时间；`start_at/end_at` 存 UTC+8 本地时间，接口按 ISO 返回带时区。
3. **迟到参赛**：允许，但罚时从 `start_at` 起算（不补偿）。
4. **冻结期**：提交照常判题，只是公开榜单看不到。
5. **取消比赛**：保留提交与榜单，页面标记"已取消"。
6. **误操作**：发布/取消/定榜三个动作二次确认 + 审计。
7. **多实例**：状态推进用条件更新；榜单若要缓存用 Redis 并带 30 秒 TTL。

## 十、分阶段实施（每阶段：实现 → 可执行验证 → 你确认后再继续）

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| P1 | V26 建表 + 管理端 CRUD（创建草稿、改配置、加/删题、校验预检） | 能建草稿并加 3 题；非法配置（无题/时间倒挂/引用未发布版本）在预检里逐条被指出；普通用户调用 → 403 |
| P2 | 发布 / 取消 / 定榜 + 状态推进定时器 + 公告 | 发布后状态变 `SCHEDULED`；到点自动 `RUNNING`（可用改时间做短时验证）；取消与定榜写审计；重复推进不会发生 |
| P3 | 公开竞赛列表页 + 竞赛详情页（按设计稿实现，含 i18n） | 页面渲染真实数据；未发布比赛不可见；`DRAFT` 直接访问 → 404 |
| P4 | 报名 + 提交归属 + 实时榜单 + 冻结 | 两个账号交叉验证罚时（错 2 次 +40min）与冻结可见性（冻结后新 AC 不出现在公开榜、管理榜可见、赛后出现）；赛前/赛后各一条提交不计入 |
| P5 | 定榜快照 + 竞赛内 rejudge + 通知（可选） | 定榜后榜单不再随数据变化；重判后榜单与快照一致 |

## 十一、待你确认的决策点

1. **赛制**：本期只做 ACM，IOI 只留字段不实现 —— 可以吗？
2. **报名**：是否需要"参赛密码 / 人数上限"（我已预留字段，实现成本很低）？
3. **限流**：竞赛期间把每用户 6 次/分放宽到多少（建议 12 次/分，按场次覆盖）？
4. **判题并发**：竞赛开始瞬间可能几十人同时提交，是否需要给竞赛期间的沙箱并发单独设上限（避免把普通判题挤掉）？
5. **Rating**：本期不做，确认？
