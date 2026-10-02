-- G4：Agent 每人每日用量计数。
--
-- 为什么单独建表：Agent 调用次数需要**原子自增**并在入口快速判断，
-- 从 agent_messages/agent_findings 现算既慢又依赖消息表语义；一张主键为 (user_id, usage_day) 的小表
-- 用 INSERT ... ON DUPLICATE KEY UPDATE 就能做到无锁竞争下的准确计数，同时天然提供「今天用了多少次」的查询。
CREATE TABLE IF NOT EXISTS agent_usage_daily (
    user_id   BIGINT      NOT NULL,
    usage_day DATE        NOT NULL,
    calls     INT         NOT NULL DEFAULT 0,
    updated_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, usage_day)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
