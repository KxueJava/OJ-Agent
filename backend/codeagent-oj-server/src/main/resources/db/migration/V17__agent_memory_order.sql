-- Agent 对话记忆：顺序与入记忆标记
-- 背景：原先靠 (created_at DESC, id DESC) 排序，但 same-turn 的两条消息落在同一秒（TIMESTAMP 秒级精度），
--       而 id 是 ThreadLocalRandom 随机 long，导致"用户问题/Agent 回答"的先后不可靠。

-- 1) seq：单调递增的物理顺序，记忆与审计都按它排序
ALTER TABLE agent_messages
    ADD COLUMN seq BIGINT NOT NULL AUTO_INCREMENT,
    ADD UNIQUE KEY uk_agent_messages_seq (seq);

-- 2) in_memory：是否参与模型上下文。被 Safety 拦截的轮次、以及模型不可用时的规则兜底文案，
--    只留审计、不进记忆，否则"给我标准答案"这类内容会被反复带回上下文。
ALTER TABLE agent_messages
    ADD COLUMN in_memory TINYINT(1) NOT NULL DEFAULT 1;

-- 3) 审计可读性：秒级 → 毫秒级
ALTER TABLE agent_messages
    MODIFY COLUMN created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
