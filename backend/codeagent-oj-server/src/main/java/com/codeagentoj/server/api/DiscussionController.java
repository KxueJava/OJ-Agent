package com.codeagentoj.server.api;

import com.codeagentoj.server.discussion.DiscussionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 讨论区接口（阶段一/二）：列表、发帖、详情、回复、采纳。
 * 列表与详情是公开可读（permitAll），发帖/回复/采纳需要登录。
 */
@RestController
@RequestMapping("/api/discussions")
public class DiscussionController {

    private final DiscussionService service;

    public DiscussionController(DiscussionService service) { this.service = service; }

    public record CreateRequest(String category, @NotBlank @Size(max = 200) String title,
                                @NotBlank @Size(max = 20000) String body, String problemSlug) {}

    public record ReplyRequest(@NotBlank @Size(max = 20000) String body, Long quotedPostId) {}

    @GetMapping public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String category,
                                                                  @RequestParam(required = false) String query,
                                                                  @RequestParam(defaultValue = "latest") String sort,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(category, query, sort, page, size));
    }

    @PostMapping public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateRequest request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.create(uid(jwt), request.category(), request.title(), request.body(), request.problemSlug()));
    }

    @GetMapping("/{id}") public ApiResponse<Map<String, Object>> detail(@PathVariable long id,
                                                                       @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.detail(id, jwt == null ? null : Long.parseLong(jwt.getSubject())));
    }

    @PostMapping("/{id}/posts") public ApiResponse<Map<String, Object>> reply(@PathVariable long id,
                                                                             @Valid @RequestBody ReplyRequest request,
                                                                             @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.reply(uid(jwt), id, request.body(), request.quotedPostId()));
    }

    @PostMapping("/{id}/posts/{postId}/accept") public ApiResponse<Map<String, Object>> accept(@PathVariable long id,
                                                                                               @PathVariable long postId,
                                                                                               @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.accept(uid(jwt), id, postId));
    }

    private long uid(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return Long.parseLong(jwt.getSubject());
    }

    /** 管理端：置顶 / 取消置顶。 */
    @PostMapping("/{id}/pin") public ApiResponse<Map<String, Object>> pin(@PathVariable long id,
                                                                        @RequestParam(defaultValue = "true") boolean value,
                                                                        @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.setPinned(admin(jwt), id, value));
    }

    /** 管理端：锁定 / 解锁。 */
    @PostMapping("/{id}/lock") public ApiResponse<Map<String, Object>> lock(@PathVariable long id,
                                                                          @RequestParam(defaultValue = "true") boolean value,
                                                                          @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.setLocked(admin(jwt), id, value));
    }

    /** 管理端：删除主题帖（及其楼层）。 */
    @DeleteMapping("/{id}") public ApiResponse<Map<String, Object>> remove(@PathVariable long id,
                                                                          @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.deleteThread(admin(jwt), id));
    }

    /** Agent 摘要：楼主或管理员触发。 */
    @PostMapping("/{id}/summary") public ApiResponse<Map<String, Object>> summary(@PathVariable long id,
                                                                                 @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.summarize(uid(jwt), id, isAdmin(jwt)));
    }

    private long admin(Jwt jwt) {
        if (!isAdmin(jwt)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        return Long.parseLong(jwt.getSubject());
    }

    private boolean isAdmin(Jwt jwt) {
        if (jwt == null) return false;
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }
}