package com.codeagentoj.server.contest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ACM 赛制榜单计算（P4），纯函数、无 IO，便于单测。
 *
 * <p>规则（与 docs/contest-plan.md 一致）：
 * <ul>
 *   <li>只统计 {@code [startAt, endAt)} 内的提交 —— 赛前/赛后提交自动排除；</li>
 *   <li>每题取**首次 AC**；罚时 = (首次 AC 时刻 − 开赛时刻) + 罚时分钟 × 该题首次 AC 之前的非 AC 次数；</li>
 *   <li>未 AC 的题不计罚时、也不计入解题数；</li>
 *   <li>排序：解题数降序 → 罚时升序 → 最后 AC 时刻升序 → userId 升序（最后一项保证**确定性排序**，避免同分抖动）；</li>
 *   <li>冻结：{@code includeFrozen=false}（选手视角）时忽略 {@code createdAt >= freezeAt} 的提交，管理员视角不受限。</li>
 * </ul>
 */
public final class StandingsCalculator {
    private StandingsCalculator() {}

    /** state: AC / FAIL / NONE */
    public record Cell(String label, String state, Long acceptedAtSeconds, int wrongAttempts) {}

    public record Row(long userId, int solved, long penaltySeconds, Long lastAcceptedAtSeconds, List<Cell> cells) {}

    public record Submission(long userId, String label, Instant createdAt, boolean accepted) {}

    public static List<Row> compute(List<Submission> submissions, List<String> labels, Instant startAt, Instant endAt,
                                    Instant freezeAt, int penaltyMinutes, boolean includeFrozen) {
        Map<Long, Map<String, List<Submission>>> byUser = new LinkedHashMap<>();
        // 冻结期内的提交：不计入已解/罚时（封榜语义），但要在格子上标出来 ——
        // 否则选手看到的是 `·`，看起来像"从没提交过"，与"提交了但被冻结"完全不同的含义。
        Map<Long, java.util.Set<String>> frozenLabels = new LinkedHashMap<>();
        for (Submission submission : submissions) {
            if (submission.createdAt().isBefore(startAt) || !submission.createdAt().isBefore(endAt)) continue;
            if (!includeFrozen && freezeAt != null && !submission.createdAt().isBefore(freezeAt)) {
                frozenLabels.computeIfAbsent(submission.userId(), id -> new java.util.LinkedHashSet<>()).add(submission.label());
                // 也要建行（空尝试列表）：否则"只交过冻结期内提交"的选手会整行消失，比显示 · 更糟
                byUser.computeIfAbsent(submission.userId(), id -> new LinkedHashMap<>())
                        .computeIfAbsent(submission.label(), label -> new ArrayList<>());
                continue;
            }
            byUser.computeIfAbsent(submission.userId(), id -> new LinkedHashMap<>())
                    .computeIfAbsent(submission.label(), label -> new ArrayList<>())
                    .add(submission);
        }

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<Long, Map<String, List<Submission>>> entry : byUser.entrySet()) {
            List<Cell> cells = new ArrayList<>();
            int solved = 0;
            long penalty = 0;
            Long lastAccepted = null;
            for (String label : labels) {
                List<Submission> attempts = entry.getValue().getOrDefault(label, List.of()).stream()
                        .sorted(Comparator.comparing(Submission::createdAt)).toList();
                Submission firstAccepted = null;
                int wrong = 0;
                for (Submission attempt : attempts) {
                    if (attempt.accepted()) { firstAccepted = attempt; break; }
                    wrong++;
                }
                if (firstAccepted == null) {
                    cells.add(new Cell(label, attempts.isEmpty()
                            ? (frozenLabels.getOrDefault(entry.getKey(), java.util.Set.of()).contains(label) ? "FROZEN" : "NONE")
                            : "FAIL", null, wrong));
                    continue;
                }
                long acceptedAtSeconds = Duration.between(startAt, firstAccepted.createdAt()).getSeconds();
                cells.add(new Cell(label, "AC", acceptedAtSeconds, wrong));
                solved++;
                penalty += acceptedAtSeconds + (long) penaltyMinutes * 60 * wrong;
                lastAccepted = lastAccepted == null ? acceptedAtSeconds : Math.max(lastAccepted, acceptedAtSeconds);
            }
            rows.add(new Row(entry.getKey(), solved, penalty, lastAccepted, cells));
        }

        rows.sort(Comparator.comparingInt(Row::solved).reversed()
                .thenComparingLong(Row::penaltySeconds)
                .thenComparing(row -> row.lastAcceptedAtSeconds() == null ? Long.MAX_VALUE : row.lastAcceptedAtSeconds())
                .thenComparingLong(Row::userId));
        return rows;
    }
}
