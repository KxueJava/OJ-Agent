package com.codeagentoj.server.api;

import static com.codeagentoj.server.contest.ContestDtos.*;

import com.codeagentoj.server.contest.ContestService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * 竞赛公开接口（P3）：任何人可访问，只看得到已发布的比赛。
 *
 * <p>可见性规则：DRAFT / CANCELLED 一律 404；SCHEDULED 阶段只公布题号与分值，
 * 题目标题与标识在开赛（RUNNING）后才出现。
 */
@RestController
@RequestMapping("/api/contests")
public class PublicContestController {
    private final ContestService service;

    public PublicContestController(ContestService service) { this.service = service; }

    @GetMapping public ApiResponse<List<ContestRow>> list(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.publicList(page, size));
    }

    @GetMapping("/{slug}") public ApiResponse<PublicContestDetail> detail(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.publicDetail(slug, jwt == null ? null : Long.parseLong(jwt.getSubject())));
    }

    /**
     * 我报名的比赛（登录后调用）。字面量路径优先于 /{slug}，不会互相冲突。
     */
    @GetMapping("/mine") public ApiResponse<java.util.List<java.util.Map<String, Object>>> mine(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(jwt == null ? java.util.List.of() : service.myContests(Long.parseLong(jwt.getSubject())));
    }

    /**
     * 竞赛期间 Agent 锁的判定（按题目），前端据此禁用助手。
     * 必须带 problemSlug：不带题目上下文时一律不锁 —— 否则"报名了进行中的比赛就全局锁定"会误伤普通题库的题。
     */
    @GetMapping("/active") public ApiResponse<java.util.Map<String, Object>> active(@AuthenticationPrincipal Jwt jwt,
                                                                                 @RequestParam(required = false) String problemSlug) {
        if (jwt == null) return ApiResponse.ok(java.util.Map.of("locked", false));
        String slug = service.lockedContestSlugForProblem(Long.parseLong(jwt.getSubject()), problemSlug);
        return ApiResponse.ok(slug == null
                ? java.util.Map.of("locked", false)
                : java.util.Map.of("locked", true, "slug", slug));
    }

    /** 报名：需要登录；重复报名幂等（返回 registered=true 与当前报名人数）。 */
    @PostMapping("/{slug}/register") public ApiResponse<RegisterResult> register(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "需要登录后才能报名");
        return ApiResponse.ok(service.register(slug, Long.parseLong(jwt.getSubject())));
    }

    /** 榜单（选手视角）：冻结期内的提交不计入。管理员视角在 /api/admin/contests/{id}/standings。 */
    @GetMapping("/{slug}/standings") public ApiResponse<List<StandingView>> standings(@PathVariable String slug) {
        return ApiResponse.ok(service.standings(slug, false));
    }
}
