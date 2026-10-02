package com.codeagentoj.server.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 发布校验回归：合法草稿通过；常见配置错误必须被逐条指出（而不是只给一句"发布失败"）。 */
class ContestValidatorTest {
    private static final Instant NOW = Instant.parse("2026-10-04T11:00:00Z");
    private static final Instant START = Instant.parse("2026-10-04T11:00:00Z");
    private static final Instant END = Instant.parse("2026-10-04T13:00:00Z");

    private static ContestValidator.Problem problem(String label, boolean published) {
        return new ContestValidator.Problem(2000L + label.charAt(0), label, 100, label.charAt(0) - 'A', published);
    }

    private static ContestValidator.Draft draft(List<ContestValidator.Problem> problems) {
        return new ContestValidator.Draft("weekly-07", "2026 秋季周赛 #07", START, END, 20, 20, problems);
    }

    private static List<String> codes(List<ContestValidator.Issue> issues) {
        return issues.stream().map(ContestValidator.Issue::code).toList();
    }

    @Test void validDraftHasNoIssues() {
        assertTrue(ContestValidator.validate(draft(List.of(problem("A", true), problem("B", true))), NOW).isEmpty());
    }

    @Test void rejectsEmptyProblemSet() {
        assertEquals(List.of("problems.empty"), codes(ContestValidator.validate(draft(List.of()), NOW)));
    }

    @Test void rejectsInvertedAndTooShortSchedule() {
        ContestValidator.Draft inverted = new ContestValidator.Draft("weekly-07", "x", END, START, 20, 20, List.of(problem("A", true)));
        // 时间倒挂时只报这一条：时长/冻结都是从它派生出来的结论，报多了是噪声
        assertEquals(List.of("time.inverted"), codes(ContestValidator.validate(inverted, NOW)));

        ContestValidator.Draft tooShort = new ContestValidator.Draft("weekly-07", "x", START, START.plusSeconds(120), 1, 20, List.of(problem("A", true)));
        assertEquals(List.of("time.tooShort"), codes(ContestValidator.validate(tooShort, NOW)));
    }

    @Test void rejectsFreezeNotShorterThanDuration() {
        ContestValidator.Draft freezeTooLong = new ContestValidator.Draft("weekly-07", "x", START, END, 120, 20, List.of(problem("A", true)));
        assertEquals(List.of("freeze.tooLong"), codes(ContestValidator.validate(freezeTooLong, NOW)));
    }

    @Test void rejectsUnpublishedProblemVersion() {
        assertEquals(List.of("problem.unpublished"),
                codes(ContestValidator.validate(draft(List.of(problem("A", true), problem("B", false))), NOW)));
    }

    @Test void rejectsDuplicateAndNonSequentialLabels() {
        assertEquals(List.of("label.duplicate"),
                codes(ContestValidator.validate(draft(List.of(problem("A", true), problem("A", true))), NOW)));
        assertEquals(List.of("label.notSequential"),
                codes(ContestValidator.validate(draft(List.of(problem("A", true), problem("C", true))), NOW)));
    }

    @Test void rejectsBadSlugAndBlankTitle() {
        List<String> issues = codes(ContestValidator.validate(
                new ContestValidator.Draft("Bad Slug!", " ", START, END, 20, 20, List.of(problem("A", true))), NOW));
        assertEquals(List.of("slug.invalid", "title.blank"), issues);
    }

    @Test void reportsEveryProblemAtOnce() {
        ContestValidator.Draft broken = new ContestValidator.Draft("x", "", START, END, 999, -1, new ArrayList<>());
        List<String> issues = codes(ContestValidator.validate(broken, NOW));
        assertEquals(List.of("slug.invalid", "title.blank", "freeze.tooLong", "penalty.negative", "problems.empty"), issues);
    }
}
