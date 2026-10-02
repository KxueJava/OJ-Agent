package com.codeagentoj.server.contest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 竞赛状态推进（P2）。
 *
 * <p>两条自动迁移都用**条件更新**完成：即使多实例同时跑，`WHERE status='SCHEDULED'` 也保证只有一次生效。
 * 时间判定一律用 DB 时间（NOW()），不用应用服务器时间，避免多实例/时区差异导致提前或延迟开赛。
 */
@Component
public class ContestScheduler {
    private static final Logger log = LoggerFactory.getLogger(ContestScheduler.class);

    private final JdbcTemplate jdbc;

    public ContestScheduler(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelayString = "${app.contest.sweep-ms:30000}")
    public void sweep() {
        int started = jdbc.update("UPDATE contests SET status='RUNNING' WHERE status='SCHEDULED' AND start_at<=NOW()");
        int ended = jdbc.update("UPDATE contests SET status='ENDED' WHERE status='RUNNING' AND end_at<=NOW()");
        if (started > 0) log.info("CONTEST_STARTED count={}（SCHEDULED → RUNNING）", started);
        if (ended > 0) log.info("CONTEST_ENDED count={}（RUNNING → ENDED，等待管理员定榜）", ended);
    }
}
