-- 判题队列治理：outbox 死信标记
--
-- 投递失败超过上限后不再无限重试，落到 DEAD 等待人工处置（管理端可查看与重投）。
-- 注意：轮询用的索引 V4 已经建好了（idx_outbox_pending (status, available_at, created_at)），
--       这里不要再加同名索引，否则 ALTER 会因 Duplicate key name 整体回滚。
ALTER TABLE outbox_events
    ADD COLUMN dead_lettered_at TIMESTAMP NULL;
