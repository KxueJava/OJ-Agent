package com.codeagentoj.server.api;

import com.codeagentoj.server.problem.ProblemDtos.*;
import com.codeagentoj.server.problem.ProblemService;
import com.codeagentoj.server.submission.OutboxMonitor;
import com.codeagentoj.server.submission.SubmissionService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ProblemService problems;
    private final SubmissionService submissions;
    private final OutboxMonitor outbox;
    private final JdbcTemplate jdbc;

    public AdminController(ProblemService problems, SubmissionService submissions, OutboxMonitor outbox, JdbcTemplate jdbc) { this.problems = problems; this.submissions = submissions; this.outbox = outbox; this.jdbc = jdbc; }

    public record RejudgeRequest(long problemVersionId, Integer limit) {}

    public record ProblemAdminRow(String slug, String title, String difficulty, String status, Long publishedVersion, Instant updatedAt) {}
    public record VersionAdmin(long id, int versionNo, String status, Instant createdAt, Instant publishedAt, int exampleCount, int testCaseCount) {}
    public record TestCaseAdmin(long id, String visibility, String input, String expectedOutput, int weight) {}
    public record ProblemManagement(ProblemAdminRow problem, List<VersionAdmin> versions, List<TestCaseAdmin> testCases) {}
    public record AgentAudit(long id, long sessionId, String intent, String route, String safetyStatus, String blockedReason, Instant createdAt) {}
    public record UserAdminRow(long id, String username, String email, String displayName, String role, boolean enabled, Instant createdAt) {}

    @GetMapping("/ping") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> ping() { return ApiResponse.ok("ADMIN"); }

    @GetMapping("/problems") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<ProblemAdminRow>> catalog() {
        return ApiResponse.ok(jdbc.query("SELECT p.slug,p.title,p.difficulty,p.status,p.published_version_id,p.updated_at FROM problems p ORDER BY p.updated_at DESC", (rs,n) -> new ProblemAdminRow(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getObject(5,Long.class),rs.getTimestamp(6).toInstant())));
    }

    @GetMapping("/problems/{slug}/management") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProblemManagement> management(@PathVariable String slug) {
        Map<String,Object> row = jdbc.queryForList("SELECT slug,title,difficulty,status,published_version_id,updated_at FROM problems WHERE slug=?",slug).stream().findFirst().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"题目不存在"));
        ProblemAdminRow problem = new ProblemAdminRow((String)row.get("slug"),(String)row.get("title"),(String)row.get("difficulty"),(String)row.get("status"),row.get("published_version_id") == null ? null : ((Number)row.get("published_version_id")).longValue(),((java.sql.Timestamp)row.get("updated_at")).toInstant());
        long problemId = ((Number)jdbc.queryForObject("SELECT id FROM problems WHERE slug=?",Long.class,slug)).longValue();
        List<VersionAdmin> versions = jdbc.query("SELECT pv.id,pv.version_no,pv.status,pv.created_at,pv.published_at,(SELECT COUNT(*) FROM examples e WHERE e.problem_version_id=pv.id),(SELECT COUNT(*) FROM test_cases tc WHERE tc.problem_version_id=pv.id) FROM problem_versions pv WHERE pv.problem_id=? ORDER BY pv.version_no DESC",(rs,n)->new VersionAdmin(rs.getLong(1),rs.getInt(2),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getTimestamp(5)==null?null:rs.getTimestamp(5).toInstant(),rs.getInt(6),rs.getInt(7)),problemId);
        List<TestCaseAdmin> cases = jdbc.query("SELECT tc.id,tc.visibility,tc.input_text,tc.expected_output,tc.weight FROM test_cases tc JOIN problem_versions pv ON pv.id=tc.problem_version_id WHERE pv.problem_id=? ORDER BY pv.version_no DESC,tc.id",(rs,n)->new TestCaseAdmin(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)),problemId);
        return ApiResponse.ok(new ProblemManagement(problem,versions,cases));
    }

    @GetMapping("/agent/audits") @PreAuthorize("hasRole('ADMIN')")    public ApiResponse<List<AgentAudit>> audits(@RequestParam(defaultValue="100") int limit) {
        int safeLimit = Math.min(Math.max(limit,1),500);
        return ApiResponse.ok(jdbc.query("SELECT id,session_id,intent,route,safety_status,blocked_reason,created_at FROM agent_audits ORDER BY created_at DESC LIMIT " + safeLimit,(rs,n)->new AgentAudit(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getTimestamp(7).toInstant())));
    }

    @PostMapping("/submissions/rejudge") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<java.util.Map<String,Object>> rejudge(@RequestBody RejudgeRequest request) {
        int rejudged = submissions.rejudgeByVersion(request.problemVersionId(), request.limit() == null ? 200 : request.limit());
        return ApiResponse.ok(java.util.Map.of("problemVersionId", request.problemVersionId(), "rejudged", rejudged));
    }

    @GetMapping("/judge/queue") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<java.util.Map<String,Object>> judgeQueue() {
        OutboxMonitor.Stats stats = outbox.stats();
        var deadEvents = jdbc.query("SELECT id,aggregate_id,event_type,attempts,last_error,dead_lettered_at FROM outbox_events WHERE status='DEAD' ORDER BY dead_lettered_at DESC LIMIT 50",
                (rs,n) -> {
                    java.util.Map<String,Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", rs.getLong(1)); row.put("aggregateId", rs.getLong(2)); row.put("eventType", rs.getString(3));
                    row.put("attempts", rs.getInt(4)); row.put("lastError", rs.getString(5) == null ? "" : rs.getString(5));
                    row.put("deadLetteredAt", rs.getTimestamp(6) == null ? "" : rs.getTimestamp(6).toInstant().toString());
                    return row;
                });
        java.util.Map<String,Object> body = new java.util.LinkedHashMap<>();
        body.put("pending", stats.pending()); body.put("published", stats.published()); body.put("dead", stats.dead());
        body.put("oldestPendingSeconds", stats.oldestPendingSeconds() == null ? 0 : stats.oldestPendingSeconds());
        body.put("deadEvents", deadEvents);
        return ApiResponse.ok(body);
    }

    @PostMapping("/judge/outbox/{id}/requeue") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<java.util.Map<String,Object>> requeue(@PathVariable long id) {
        int changed = jdbc.update("UPDATE outbox_events SET status='PENDING',attempts=0,last_error=NULL,available_at=CURRENT_TIMESTAMP,dead_lettered_at=NULL WHERE id=? AND status='DEAD'", id);
        return ApiResponse.ok(java.util.Map.of("requeued", changed, "id", id));
    }

    @GetMapping("/users") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserAdminRow>> users(@RequestParam(defaultValue="") String query) {
        String term = "%" + query.trim() + "%";
        return ApiResponse.ok(jdbc.query("SELECT u.id,u.username,u.email,u.display_name,r.name,u.enabled,u.created_at FROM users u JOIN roles r ON r.id=u.role_id WHERE u.username LIKE ? OR u.email LIKE ? OR u.display_name LIKE ? ORDER BY u.created_at DESC LIMIT 200", (rs,n) -> new UserAdminRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBoolean(6),rs.getTimestamp(7).toInstant()), term, term, term));
    }

    @PostMapping("/users/{id}/status") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserAdminRow> userStatus(@PathVariable long id, @RequestParam boolean enabled, @AuthenticationPrincipal Jwt jwt) {
        if (id == Long.parseLong(jwt.getSubject()) && !enabled) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "不能禁用当前管理员账号");
        int changed = jdbc.update("UPDATE users SET enabled=? WHERE id=?", enabled, id);
        if (changed == 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "用户不存在");
        if (!enabled) jdbc.update("UPDATE refresh_tokens SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", id);
        Map<String,Object> user = jdbc.queryForList("SELECT u.id,u.username,u.email,u.display_name,r.name,u.enabled,u.created_at FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=?",id).getFirst();
        return ApiResponse.ok(new UserAdminRow(((Number)user.get("id")).longValue(),(String)user.get("username"),(String)user.get("email"),(String)user.get("display_name"),(String)user.get("name"),(Boolean)user.get("enabled"),((java.sql.Timestamp)user.get("created_at")).toInstant()));
    }

    @PostMapping("/problems") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminProblemView> create(@Valid @RequestBody CreateProblemRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(problems.create(request, Long.parseLong(jwt.getSubject()))); }
    @PostMapping("/problems/{slug}/versions") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminProblemView> version(@PathVariable String slug, @Valid @RequestBody VersionRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(problems.addVersion(slug,request,Long.parseLong(jwt.getSubject()))); }
    @PostMapping("/problems/{slug}/publish") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminProblemView> publish(@PathVariable String slug) { return ApiResponse.ok(problems.publish(slug)); }
    @PostMapping("/problems/{slug}/offline") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminProblemView> offline(@PathVariable String slug) { return ApiResponse.ok(problems.unpublish(slug)); }
}
