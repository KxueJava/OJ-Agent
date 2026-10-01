package com.codeagentoj.server.agent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动诊断的节流器：既不静默丢诊断，也不让成本失控。
 *
 * <p>两条规则（可单测，时间由调用方传入，便于测试）：
 * <ul>
 *   <li><b>冷却</b>：同一「用户 + 题目」在 {@code cooldownMs} 内只自动诊断一次 —— 吸收连点与重复提交；</li>
 *   <li><b>额度</b>：同一用户每个滚动小时最多 {@code maxPerHour} 次自动诊断 —— 这才是真正的成本闸。</li>
 * </ul>
 *
 * <p>刻意不做"同一题 5 分钟只诊断一次"：那样会把"改一版→提交→看诊断"的正循环堵死，
 * 而且调用方无法把原因告诉用户。{@link #check} 是只读的，因此查询接口可以据此给出准确原因。
 */
public class DiagnosisThrottle {
    public enum Decision { ALLOW, COOLDOWN, HOURLY_CAP }

    private static final long HOUR_MS = 3_600_000L;

    private final long cooldownMs;
    private final int maxPerHour;
    private final Map<String, Long> lastByKey = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Long>> recentByUser = new ConcurrentHashMap<>();

    public DiagnosisThrottle(long cooldownSeconds, int maxPerHour) {
        this.cooldownMs = Math.max(0, cooldownSeconds) * 1000L;
        this.maxPerHour = Math.max(1, maxPerHour);
    }

    public Decision check(long userId, long problemVersion, long now) {
        Long last = lastByKey.get(key(userId, problemVersion));
        if (cooldownMs > 0 && last != null && now - last < cooldownMs) return Decision.COOLDOWN;
        Deque<Long> stamps = recentByUser.get(userId);
        if (stamps != null) {
            synchronized (stamps) {
                purge(stamps, now);
                if (stamps.size() >= maxPerHour) return Decision.HOURLY_CAP;
            }
        }
        return Decision.ALLOW;
    }

    /** 真正派发诊断任务时调用，记录一次消耗。 */
    public void record(long userId, long problemVersion, long now) {
        lastByKey.put(key(userId, problemVersion), now);
        Deque<Long> stamps = recentByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (stamps) {
            purge(stamps, now);
            stamps.addLast(now);
        }
    }

    private static void purge(Deque<Long> stamps, long now) {
        while (!stamps.isEmpty() && now - stamps.peekFirst() > HOUR_MS) stamps.pollFirst();
    }

    private static String key(long userId, long problemVersion) {
        return userId + ":" + problemVersion;
    }
}
