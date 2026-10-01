package com.codeagentoj.server.dashboard;

import com.codeagentoj.server.api.ApiResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ProblemItem(String slug, String title, String difficulty, String topic, String state, Integer latestRuntimeMs) {}
    public record ContinueItem(String slug, String title, String topic, String state, Instant updatedAt, Integer submissionCount) {}
    public record Dashboard(ContinueItem continueItem, List<ProblemItem> queue, int totalProblems, int solvedCount, int totalSubmissions,
                            int acceptedSubmissions, int recentSubmissions, int activeDays) {}

    @GetMapping
    public ApiResponse<Dashboard> dashboard(@AuthenticationPrincipal Jwt jwt) {
        long userId = userId(jwt);
        Map<String, Object> latest = jdbc.queryForList("""
                SELECT p.slug,p.title,p.difficulty,MAX(s.created_at) updated_at,COUNT(*) submission_count,
                       MAX(s.status='AC') solved
                FROM submissions s JOIN problems p ON p.id=s.problem_id
                WHERE s.user_id=? GROUP BY p.id,p.slug,p.title,p.difficulty
                ORDER BY updated_at DESC LIMIT 1
                """, userId).stream().findFirst().orElse(null);
        ContinueItem continueItem = latest == null ? null : new ContinueItem(
                (String) latest.get("slug"), (String) latest.get("title"),
                topic(latest.get("difficulty")), "继续", timestamp(latest.get("updated_at")),
                ((Number) latest.get("submission_count")).intValue());
        List<ProblemItem> queue = jdbc.query("""
                SELECT p.slug,p.title,p.difficulty,
                       COALESCE((SELECT s.status FROM submissions s WHERE s.user_id=? AND s.problem_id=p.id ORDER BY s.created_at DESC LIMIT 1),'NEW') latest_status,
                       (SELECT s.runtime_ms FROM submissions s WHERE s.user_id=? AND s.problem_id=p.id ORDER BY s.created_at DESC LIMIT 1) latest_runtime
                FROM problems p WHERE p.status='PUBLISHED' ORDER BY p.updated_at DESC LIMIT 10
                """, (rs, row) -> new ProblemItem(rs.getString(1), rs.getString(2), rs.getString(3), topic(rs.getString(3)), state(rs.getString(4)), rs.getObject(5, Integer.class)), userId, userId);
        int totalProblems = jdbc.queryForObject("SELECT COUNT(*) FROM problems WHERE status='PUBLISHED'", Integer.class);
        int total = count("SELECT COUNT(*) FROM submissions WHERE user_id=?", userId);
        int accepted = count("SELECT COUNT(*) FROM submissions WHERE user_id=? AND status='AC'", userId);
        int solved = count("SELECT COUNT(DISTINCT problem_id) FROM submissions WHERE user_id=? AND status='AC'", userId);
        int recent = count("SELECT COUNT(*) FROM submissions WHERE user_id=? AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 DAY)", userId);
        int activeDays = count("SELECT COUNT(DISTINCT DATE(created_at)) FROM submissions WHERE user_id=? AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 DAY)", userId);
        return ApiResponse.ok(new Dashboard(continueItem, queue, totalProblems, solved, total, accepted, recent, activeDays));
    }

    private int count(String sql, long userId) { return jdbc.queryForObject(sql, Integer.class, userId); }
    private String state(String status) { return switch (status) { case "AC" -> "复习"; case "NEW" -> "开始"; default -> "继续"; }; }
    private String topic(Object difficulty) { return "EASY".equals(difficulty) ? "基础练习" : "算法练习"; }
    private Instant timestamp(Object value) { return value instanceof java.sql.Timestamp timestamp ? timestamp.toInstant() : null; }
    private long userId(Jwt jwt) { if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"); return Long.parseLong(jwt.getSubject()); }
}
