-- 竞赛公告（P2）：管理员在比赛前后发通知，选手在竞赛页与详情页看到
CREATE TABLE contest_announcements (
    id BIGINT PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_contest_announcement (contest_id, created_at)
);
