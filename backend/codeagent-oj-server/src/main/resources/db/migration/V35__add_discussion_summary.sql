-- 讨论区 Agent 摘要（阶段三）：摘要正文与生成时间。
-- 只读展示用；生成入口是 POST /api/discussions/{id}/summary（楼主或管理员触发）。
ALTER TABLE discussions
    ADD COLUMN summary_md MEDIUMTEXT NULL,
    ADD COLUMN summary_at TIMESTAMP NULL;
