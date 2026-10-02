package com.codeagentoj.server.api;

import static com.codeagentoj.server.contest.ContestDtos.*;

import com.codeagentoj.server.contest.ContestService;
import com.codeagentoj.server.submission.SubmissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** 竞赛管理端接口（P1：草稿与配置、选题、发布校验预检）。发布/取消/定榜在 P2 接入。 */
@RestController
@RequestMapping("/api/admin/contests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContestController {
    private final ContestService service;
    private final SubmissionService submissions;

    public AdminContestController(ContestService service, SubmissionService submissions) { this.service = service; this.submissions = submissions; }

    @GetMapping public ApiResponse<List<ContestRow>> list(@RequestParam(defaultValue = "") String status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(status, page, size));
    }

    @PostMapping public ApiResponse<ContestDetail> create(@Valid @RequestBody CreateRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.create(request, admin(jwt)));
    }

    @GetMapping("/{id}") public ApiResponse<ContestDetail> detail(@PathVariable long id) { return ApiResponse.ok(service.detail(id)); }

    @PutMapping("/{id}") public ApiResponse<ContestDetail> update(@PathVariable long id, @Valid @RequestBody UpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.update(id, request, admin(jwt)));
    }

    @PostMapping("/{id}/problems") public ApiResponse<ContestDetail> addProblem(@PathVariable long id, @Valid @RequestBody AddProblemRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.addProblem(id, request, admin(jwt)));
    }

    @DeleteMapping("/{id}/problems/{problemId}") public ApiResponse<ContestDetail> removeProblem(@PathVariable long id, @PathVariable long problemId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.removeProblem(id, problemId, admin(jwt)));
    }

    /** 发布：校验不通过时返回 published=false 与逐条原因（HTTP 200），前端直接渲染校验清单。 */
    @PostMapping("/{id}/publish") public ApiResponse<PublishResult> publish(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.publish(id, admin(jwt)));
    }

    @PostMapping("/{id}/cancel") public ApiResponse<ContestDetail> cancel(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.cancel(id, admin(jwt)));
    }

    @PostMapping("/{id}/finalize") public ApiResponse<ContestDetail> finalizeContest(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.finalizeContest(id, admin(jwt)));
    }

    @PostMapping("/{id}/announcements") public ApiResponse<List<AnnouncementView>> announce(@PathVariable long id, @Valid @RequestBody AnnounceRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.announce(id, request.body(), admin(jwt)));
    }

    /** 管理员视角榜单：不受冻结限制（选手端 /api/contests/{slug}/standings 会隐藏冻结后的提交）。 */
    @GetMapping("/{id}/standings") public ApiResponse<List<StandingView>> standings(@PathVariable long id) {
        return ApiResponse.ok(service.standingsByContestId(id, true));
    }

    /** 竞赛内批量重判（P5）：赛中发现数据问题或赛后修正口径时用；复用已有的重判链路。 */
    @PostMapping("/{id}/rejudge") public ApiResponse<java.util.Map<String,Object>> rejudge(@PathVariable long id, @RequestParam(defaultValue = "500") int limit, @AuthenticationPrincipal Jwt jwt) {
        int rejudged = submissions.rejudgeByContest(id, limit);
        return ApiResponse.ok(java.util.Map.of("contestId", id, "rejudged", rejudged));
    }

    private long admin(Jwt jwt) {
        if (jwt == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "需要登录");
        return Long.parseLong(jwt.getSubject());
    }
}
