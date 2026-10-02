package com.codeagentoj.server.contest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ContestDtos {
    private ContestDtos() {}

    public record CreateRequest(@NotBlank @Size(max = 64) String slug, @NotBlank @Size(max = 128) String title,
                                @Size(max = 20000) String descriptionMd, Instant startAt, Instant endAt,
                                Integer freezeMinutes, Integer penaltyMinutes, Integer maxParticipants, String password) {}

    public record UpdateRequest(@Size(max = 128) String title, @Size(max = 20000) String descriptionMd, Instant startAt, Instant endAt,
                                Integer freezeMinutes, Integer penaltyMinutes, Integer maxParticipants, String password) {}

    public record AddProblemRequest(@NotBlank String problemSlug, Integer score) {}

    public record ProblemView(long id, long problemId, long problemVersionId, String label, int score, int displayOrder,
                              String slug, String title, String difficulty) {}

    /** 校验结论：code 供前端定位与 i18n，message 可直接展示。 */
    public record IssueView(String code, String message) {}

    public record ContestRow(long id, String slug, String title, String mode, String status, Instant startAt, Instant endAt,
                             int freezeMinutes, int penaltyMinutes, int problemCount, long participantCount,
                             Instant publishedAt, Instant finalizedAt, Instant updatedAt) {}

    public record ContestDetail(ContestRow contest, String descriptionMd, List<ProblemView> problems, List<IssueView> issues,
                                List<AnnouncementView> announcements, boolean editable) {}

    /** 发布结果：校验不通过不是异常，而是一种正常结果 —— 前端直接拿 issues 渲染校验清单。 */
    public record PublishResult(boolean published, List<IssueView> issues, ContestRow contest) {}

    public record AnnounceRequest(@NotBlank @Size(max = 2000) String body) {}

    public record AnnouncementView(long id, String body, Instant createdAt) {}

    /**
     * 公开题目视图。开赛前（SCHEDULED）只公布题号与分值，题目标题/标识隐藏 ——
     * 选手在开赛时才能看到题目，避免提前刷题。
     */
    public record PublicProblemView(String label, int score, int displayOrder, String slug, String title, String difficulty) {}

    public record PublicContestDetail(ContestRow contest, String descriptionMd, List<PublicProblemView> problems,
                                      List<AnnouncementView> announcements, boolean problemsHidden, boolean registered) {}

    /** 榜单里的一格：AC 显示用时与错误次数；FAIL 表示尝试过但未过；NONE 表示未提交。 */
    public record CellView(String label, String state, Long acceptedAtSeconds, int wrongAttempts) {}

    public record StandingView(int rank, long userId, String username, String displayName, int solved, long penaltySeconds, List<CellView> cells) {}

    public record RegisterResult(boolean registered, long contestId, String status, long participantCount) {}
}
