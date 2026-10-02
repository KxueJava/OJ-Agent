package com.codeagentoj.server.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 榜单规则回归：罚时口径、未 AC 不罚时、冻结可见性、排序确定性、赛程外提交排除。 */
class StandingsCalculatorTest {
    private static final Instant START = Instant.parse("2026-10-04T11:00:00Z");
    private static final Instant END = START.plusSeconds(7200);
    private static final Instant FREEZE = START.plusSeconds(3600);   // 第 60 分钟进入冻结
    private static final List<String> LABELS = List.of("A", "B");

    private static StandingsCalculator.Submission attempt(long user, String label, int minutes, boolean accepted) {
        return new StandingsCalculator.Submission(user, label, START.plusSeconds(minutes * 60L), accepted);
    }

    @Test void penaltyCountsOnlyWrongAttemptsBeforeFirstAcceptance() {
        List<StandingsCalculator.Row> rows = StandingsCalculator.compute(List.of(
                attempt(1, "A", 10, true),
                attempt(2, "A", 5, false),
                attempt(2, "A", 8, false),
                attempt(2, "A", 30, true)
        ), LABELS, START, END, FREEZE, 20, true);

        assertEquals(2, rows.size());
        // 用户 1：10 分钟，无罚时；用户 2：30 分钟 + 2×20 分钟 = 70 分钟
        assertEquals(1, rows.get(0).userId());
        assertEquals(600, rows.get(0).penaltySeconds());
        assertEquals(2, rows.get(1).userId());
        assertEquals(70 * 60, rows.get(1).penaltySeconds());
        assertEquals(2, rows.get(1).cells().get(0).wrongAttempts());
        assertEquals(1800L, rows.get(1).cells().get(0).acceptedAtSeconds());
    }

    @Test void unsolvedProblemAddsNoPenalty() {
        List<StandingsCalculator.Row> rows = StandingsCalculator.compute(List.of(
                attempt(1, "A", 10, true),
                attempt(1, "B", 20, false),
                attempt(1, "B", 40, false)
        ), LABELS, START, END, FREEZE, 20, true);
        assertEquals(1, rows.getFirst().solved());
        assertEquals(600, rows.getFirst().penaltySeconds());
        assertEquals("FAIL", rows.getFirst().cells().get(1).state());
    }

    @Test void submissionsOutsideTheWindowAreIgnored() {
        List<StandingsCalculator.Row> rows = StandingsCalculator.compute(List.of(
                new StandingsCalculator.Submission(1, "A", START.minusSeconds(60), true),
                new StandingsCalculator.Submission(2, "A", END.plusSeconds(60), true),
                attempt(3, "A", 10, true)
        ), LABELS, START, END, FREEZE, 20, true);
        assertEquals(1, rows.size());
        assertEquals(3, rows.getFirst().userId());
    }

    @Test void freezeHidesLateAcceptedSubmissionsForPlayersButNotForAdmins() {
        List<StandingsCalculator.Submission> data = List.of(
                attempt(1, "A", 10, true),
                attempt(2, "A", 66, true));   // 冻结开始于第 60 分钟
        List<StandingsCalculator.Row> playerView = StandingsCalculator.compute(data, LABELS, START, END, FREEZE, 20, false);
        // 选手视角：两人都在榜上（用户 2 不能整行消失，否则看起来像"没参加过"），
        // 但用户 2 的这次 AC 落在冻结期内 → 该格标 FROZEN，且不计入已解/罚时。
        assertEquals(2, playerView.size());
        StandingsCalculator.Row first = playerView.stream().filter(row -> row.userId() == 1L).findFirst().orElseThrow();
        StandingsCalculator.Row second = playerView.stream().filter(row -> row.userId() == 2L).findFirst().orElseThrow();
        assertEquals("AC", first.cells().getFirst().state());
        assertEquals(1, first.solved());
        assertEquals("FROZEN", second.cells().getFirst().state());
        assertEquals(0, second.solved());
        assertEquals(0L, second.penaltySeconds());

        List<StandingsCalculator.Row> adminView = StandingsCalculator.compute(data, LABELS, START, END, FREEZE, 20, true);
        assertEquals(2, adminView.size());
        StandingsCalculator.Row adminSecond = adminView.stream().filter(row -> row.userId() == 2L).findFirst().orElseThrow();
        assertEquals("AC", adminSecond.cells().getFirst().state());
        assertEquals(1, adminSecond.solved());
    }

    @Test void tieBreakIsDeterministicByLastAcceptanceThenUserId() {
        // 三个人都是 2 题：7 与 9 的罚时完全相同（10+11 分钟）→ 按 userId 升序 → 7 在 9 前；5 的罚时更长（20+10 分钟）排最后
        List<StandingsCalculator.Submission> data = List.of(
                attempt(9, "A", 10, true),
                attempt(7, "A", 10, true),
                attempt(5, "B", 10, true),
                attempt(5, "A", 20, true),
                attempt(7, "B", 11, true),
                attempt(9, "B", 11, true));
        List<StandingsCalculator.Row> rows = StandingsCalculator.compute(data, LABELS, START, END, FREEZE, 20, true);
        assertEquals(List.of(7L, 9L, 5L), rows.stream().map(StandingsCalculator.Row::userId).toList());
        assertEquals(rows.get(0).penaltySeconds(), rows.get(1).penaltySeconds());
    }
}
