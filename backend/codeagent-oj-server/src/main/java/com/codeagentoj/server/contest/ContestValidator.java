package com.codeagentoj.server.contest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 竞赛发布校验（纯函数，便于单测）。
 *
 * <p>管理端最忌讳的是"发布失败"四个字 —— 校验必须**逐条返回原因**，
 * 让管理员知道是时间倒挂、还是某道题引用的版本没发布。
 */
public final class ContestValidator {
    private ContestValidator() {}

    public static final int MIN_DURATION_MINUTES = 5;
    private static final String LABELS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** 待校验的草稿快照。 */
    public record Problem(long problemVersionId, String label, int score, int displayOrder, boolean published) {}

    public record Draft(String slug, String title, Instant startAt, Instant endAt,
                        int freezeMinutes, int penaltyMinutes, List<Problem> problems) {}

    /** 一条校验结论。code 供前端做 i18n 与定位，message 是可直接展示的中文原因。 */
    public record Issue(String code, String message) {}

    public static List<Issue> validate(Draft draft, Instant now) {
        List<Issue> issues = new ArrayList<>();
        if (draft.slug() == null || !draft.slug().matches("[a-z0-9-]{3,64}")) {
            issues.add(new Issue("slug.invalid", "标识只能是 3–64 位小写字母、数字或连字符"));
        }
        if (draft.title() == null || draft.title().isBlank()) {
            issues.add(new Issue("title.blank", "竞赛名称不能为空"));
        }
        if (draft.startAt() == null || draft.endAt() == null) {
            issues.add(new Issue("time.missing", "必须填写开始与结束时间"));
        } else if (draft.startAt().isBefore(draft.endAt())) {
            // 时间本身非法时不再叠加"冻结/时长"这类派生结论，避免同一根因报好几条噪声
            long minutes = Duration.between(draft.startAt(), draft.endAt()).toMinutes();
            if (minutes < MIN_DURATION_MINUTES) {
                issues.add(new Issue("time.tooShort", "比赛时长至少 " + MIN_DURATION_MINUTES + " 分钟"));
            }
            if (draft.freezeMinutes() < 0) {
                issues.add(new Issue("freeze.negative", "冻结时长不能为负数"));
            } else if (draft.freezeMinutes() >= minutes) {
                issues.add(new Issue("freeze.tooLong", "冻结时长必须小于比赛总时长"));
            }
        } else {
            issues.add(new Issue("time.inverted", "结束时间必须晚于开始时间"));
        }
        if (draft.penaltyMinutes() < 0) {
            issues.add(new Issue("penalty.negative", "罚时不能为负数"));
        }

        List<Problem> problems = draft.problems() == null ? List.of() : draft.problems();
        if (problems.isEmpty()) {
            issues.add(new Issue("problems.empty", "至少需要添加 1 道题"));
        }
        Set<String> labels = new HashSet<>();
        for (Problem problem : problems) {
            if (problem.label() == null || problem.label().length() != 1 || LABELS.indexOf(problem.label()) < 0) {
                issues.add(new Issue("label.invalid", "题号必须是 A–Z 的单个大写字母：" + problem.label()));
            } else if (!labels.add(problem.label())) {
                issues.add(new Issue("label.duplicate", "题号重复：" + problem.label()));
            }
            if (!problem.published()) {
                issues.add(new Issue("problem.unpublished", problem.label() + " 题引用的题目版本尚未发布"));
            }
        }
        // 题号必须从 A 开始连续，避免管理端排序后出现 A、C、D 这类空洞；
        // 但已有重复题号时不再叠加"不连续"，同一根因只报一条
        if (labels.size() == problems.size()) {
            for (int index = 0; index < problems.size(); index++) {
                String expected = String.valueOf(LABELS.charAt(index));
                String actual = problems.get(index).label();
                if (!expected.equals(actual)) {
                    issues.add(new Issue("label.notSequential", "题号必须从 A 开始连续排列，第 " + (index + 1) + " 题应为 " + expected + "，当前是 " + actual));
                    break;
                }
            }
        }
        return issues;
    }
}
