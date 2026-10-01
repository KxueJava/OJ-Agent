package com.codeagentoj.server.submission;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提交频率限制：既防刷，也保护判题沙箱（每次判题都要起容器）。
 *
 * <p>两条规则，时间由调用方传入便于测试：
 * <ul>
 *   <li>每分钟最多 {@code perMinute} 次 —— 挡住连点与脚本刷题；</li>
 *   <li>每小时最多 {@code perHour} 次 —— 挡住长时间高频占用沙箱。</li>
 * </ul>
 * {@link #check} 只读，因此"被限流"可以给出准确原因，而不是静默失败。
 */
public class SubmissionRateLimiter {
    public enum Decision { ALLOW, PER_MINUTE, PER_HOUR }

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 3_600_000L;

    private final int perMinute;
    private final int perHour;
    private final Map<Long, Deque<Long>> recentByUser = new ConcurrentHashMap<>();

    public SubmissionRateLimiter(int perMinute, int perHour) {
        this.perMinute = Math.max(1, perMinute);
        this.perHour = Math.max(this.perMinute, perHour);
    }

    public Decision check(long userId, long now) {
        Deque<Long> stamps = recentByUser.get(userId);
        if (stamps == null) return Decision.ALLOW;
        synchronized (stamps) {
            purge(stamps, now);
            if (stamps.size() >= perHour) return Decision.PER_HOUR;
            long minuteAgo = now - MINUTE_MS;
            long inLastMinute = stamps.stream().filter(stamp -> stamp > minuteAgo).count();
            if (inLastMinute >= perMinute) return Decision.PER_MINUTE;
        }
        return Decision.ALLOW;
    }

    /** 只有在提交真正被受理时才记录，被限流或被队列保护挡下的请求不计数。 */
    public void record(long userId, long now) {
        Deque<Long> stamps = recentByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (stamps) {
            purge(stamps, now);
            stamps.addLast(now);
        }
    }

    private static void purge(Deque<Long> stamps, long now) {
        while (!stamps.isEmpty() && now - stamps.peekFirst() > HOUR_MS) stamps.pollFirst();
    }
}
