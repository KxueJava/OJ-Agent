-- 判题可观测性 + 重判支持
--
-- compile_ms / failure_kind：把「用户代码问题」和「判题基础设施问题」分开记录。
--   之前沙盒不可用、worker 抛异常都只写成 RE("判题沙盒执行异常")，界面看起来像是用户代码错了，
--   老师/用户都无从判断该改代码还是该找运维。
-- rejudge_count：测试数据或沙箱升级后可以重判，记录被重判过几次。
ALTER TABLE submissions
    ADD COLUMN compile_ms INT NULL AFTER runtime_ms,
    ADD COLUMN failure_kind VARCHAR(16) NULL AFTER verdict_message,
    ADD COLUMN rejudge_count INT NOT NULL DEFAULT 0;
