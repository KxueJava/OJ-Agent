package com.codeagentoj.server.submission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 判题队列看门狗。
 *
 * <p>outbox 是判题投递的可靠缓冲，但它一旦积压或出现死信，从外面完全看不出来 ——
 * 表现只是"提交了但一直排队中"。这里定期检查并打出可 grep 的告警标记：
 * <ul>
 *   <li>{@code OUTBOX_BACKLOG}：积压条数或最老待投递时间超过阈值；</li>
 *   <li>{@code OUTBOX_DEAD_TOTAL}：死信总数增长。</li>
 * </ul>
 * 同时把统计暴露给管理端接口，便于答辩演示或排障时直接看。
 */
@Component
public class OutboxMonitor {
    private static final Logger log = LoggerFactory.getLogger(OutboxMonitor.class);

    private final JdbcTemplate jdbc;
    private final int backlogThreshold;
    private final int ageThresholdSeconds;
    private long lastDead;

    public OutboxMonitor(JdbcTemplate jdbc,
                         @Value("${app.judge.outbox.backlog-threshold:20}") int backlogThreshold,
                         @Value("${app.judge.outbox.age-threshold-seconds:60}") int ageThresholdSeconds) {
        this.jdbc = jdbc;
        this.backlogThreshold = Math.max(1, backlogThreshold);
        this.ageThresholdSeconds = Math.max(1, ageThresholdSeconds);
    }

    public record Stats(long pending, long published, long dead, Long oldestPendingSeconds) {}

    public Stats stats() {
        Long oldest = jdbc.queryForObject(
                "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(available_at), CURRENT_TIMESTAMP), 0) FROM outbox_events WHERE status='PENDING'", Long.class);
        return new Stats(count("PENDING"), count("PUBLISHED"), count("DEAD"), oldest);
    }

    @Scheduled(fixedDelayString = "${app.judge.outbox.monitor-ms:30000}")
    public void report() {
        Stats stats = stats();
        if (stats.dead() > lastDead) {
            log.warn("OUTBOX_DEAD_TOTAL dead={} pending={} —— 查看 /api/admin/judge/queue 并重投", stats.dead(), stats.pending());
            lastDead = stats.dead();
        }
        long oldest = stats.oldestPendingSeconds() == null ? 0 : stats.oldestPendingSeconds();
        if (stats.pending() >= backlogThreshold || oldest >= ageThresholdSeconds) {
            log.warn("OUTBOX_BACKLOG pending={} oldest_pending_seconds={} dead={} —— 判题投递可能阻塞", stats.pending(), oldest, stats.dead());
        }
    }

    private long count(String status) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE status=?", Long.class, status);
        return value == null ? 0 : value;
    }
}
