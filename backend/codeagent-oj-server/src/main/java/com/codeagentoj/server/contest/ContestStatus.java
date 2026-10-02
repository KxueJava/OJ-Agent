package com.codeagentoj.server.contest;

import java.util.List;
import java.util.Set;

/**
 * 竞赛状态机（P2）。
 *
 * <p>把"哪些状态允许做什么"集中成一处纯逻辑，避免散落在 Service/Controller 里各写一份判断：
 * <pre>
 * DRAFT ──发布──> SCHEDULED ──到点──> RUNNING ──到点──> ENDED ──定榜──> FINALIZED
 *   └────────────── 取消 ──────────────┘                    → CANCELLED
 * </pre>
 */
public final class ContestStatus {
    private ContestStatus() {}

    public static final String DRAFT = "DRAFT";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String RUNNING = "RUNNING";
    public static final String ENDED = "ENDED";
    public static final String FINALIZED = "FINALIZED";
    public static final String CANCELLED = "CANCELLED";

    /** 可以改配置/改题的状态。 */
    public static final Set<String> EDITABLE = Set.of(DRAFT, SCHEDULED);

    /** 定时器允许的自动推进：只允许这两条，且用条件更新保证多实例安全。 */
    public static final List<String[]> AUTO_TRANSITIONS = List.of(
            new String[]{SCHEDULED, RUNNING},
            new String[]{RUNNING, ENDED});

    public static boolean canPublish(String status) { return DRAFT.equals(status) || SCHEDULED.equals(status); }

    public static boolean canCancel(String status) { return DRAFT.equals(status) || SCHEDULED.equals(status) || RUNNING.equals(status); }

    /** 定榜：只有已结束的比赛才能定榜；FINALIZED 不可重复定榜。 */
    public static boolean canFinalize(String status) { return ENDED.equals(status); }

    public static boolean canAnnounce(String status) { return !CANCELLED.equals(status) && !DRAFT.equals(status); }

    public static boolean isTerminal(String status) { return FINALIZED.equals(status) || CANCELLED.equals(status); }
}
