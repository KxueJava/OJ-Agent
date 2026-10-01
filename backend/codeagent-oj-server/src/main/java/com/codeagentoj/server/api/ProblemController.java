package com.codeagentoj.server.api;

import com.codeagentoj.server.problem.ProblemDtos.*;
import com.codeagentoj.server.problem.ProblemService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProblemController {
    private final ProblemService problems;
    public ProblemController(ProblemService problems) { this.problems = problems; }
    @GetMapping("/problems") public ApiResponse<PageView> list(@RequestParam(required=false) String query, @RequestParam(required=false) String difficulty, @RequestParam(required=false) String tag, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(problems.list(query,difficulty,tag,page,size,userId(jwt))); }
    @GetMapping("/problems/{slug}") public ApiResponse<ProblemDetail> detail(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(problems.detail(slug,userId(jwt))); }
    @GetMapping("/tags") public ApiResponse<List<TagView>> tags() { return ApiResponse.ok(problems.tags()); }
    @PutMapping("/problems/{slug}/favorite") public ApiResponse<Void> favorite(@PathVariable String slug, @RequestParam(defaultValue="true") boolean enabled, @AuthenticationPrincipal Jwt jwt) { if (jwt==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"需要登录"); problems.favorite(slug,Long.parseLong(jwt.getSubject()),enabled); return ApiResponse.ok(null); }
    private Long userId(Jwt jwt) { return jwt == null ? null : Long.valueOf(jwt.getSubject()); }
}
