-- 竞赛功能：数据模型（docs/contest-plan.md 的 P1）
--
-- 设计取舍：提交归属用 submissions.contest_id 一列，而不是"提交表 + 竞赛提交表"双写，
-- 这样榜单能一条 SQL 从提交表现算，不会出现两份数据不一致。
-- 题目钉住 problem_version_id：赛中有人改题面/数据也不会影响正在进行的比赛。

CREATE TABLE contests (
    id BIGINT PRIMARY KEY,
    slug VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description_md TEXT NULL,
    mode VARCHAR(16) NOT NULL DEFAULT 'ACM',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    freeze_minutes INT NOT NULL DEFAULT 20,
    penalty_minutes INT NOT NULL DEFAULT 20,
    max_participants INT NULL,
    password_hash VARCHAR(100) NULL,
    published_at DATETIME NULL,
    finalized_at DATETIME NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_contests_slug (slug),
    KEY idx_contests_status_start (status, start_at)
);

CREATE TABLE contest_problems (
    id BIGINT PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    problem_version_id BIGINT NOT NULL,
    label CHAR(1) NOT NULL,
    score INT NOT NULL DEFAULT 100,
    display_order INT NOT NULL,
    UNIQUE KEY uk_contest_problem_label (contest_id, label),
    UNIQUE KEY uk_contest_problem_problem (contest_id, problem_id),
    KEY idx_contest_problems_contest (contest_id, display_order)
);

CREATE TABLE contest_participants (
    id BIGINT PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    rank_final INT NULL,
    solved_final INT NULL,
    penalty_final INT NULL,
    UNIQUE KEY uk_contest_participant (contest_id, user_id),
    KEY idx_contest_participants_user (user_id)
);

-- 提交归属：提交时若处于赛程内且已报名，由提交接口写入 contest_id（P4 接入）
ALTER TABLE submissions
    ADD COLUMN contest_id BIGINT NULL,
    ADD INDEX idx_submissions_contest (contest_id, user_id, created_at);

-- 管理动作审计：谁在何时发布/取消/定榜/改了赛程时间
CREATE TABLE admin_audit_log (
    id BIGINT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    action VARCHAR(48) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NULL,
    detail_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_admin_audit_created (created_at),
    KEY idx_admin_audit_target (target_type, target_id)
);
