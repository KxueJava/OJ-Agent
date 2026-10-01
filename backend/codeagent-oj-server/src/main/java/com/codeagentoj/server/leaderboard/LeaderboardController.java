package com.codeagentoj.server.leaderboard;

import com.codeagentoj.server.api.ApiResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
    private final JdbcTemplate jdbc;

    public LeaderboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Entry(long userId, String displayName, String username, String avatarUrl, String avatarColor,
                        int acceptedCount, Instant lastAcceptedAt, int rank) {}
    public record Leaderboard(String period, Instant since, List<Entry> entries, int totalAccepted, int activeUsers,
                              Long currentUserId, Integer currentUserRank) {}

    @GetMapping
    public ApiResponse<Leaderboard> leaderboard(@RequestParam(defaultValue = "week") String period,
                                                @AuthenticationPrincipal Jwt jwt) {
        String normalized = switch (period.toLowerCase()) {
            case "month" -> "month";
            case "all" -> "all";
            default -> "week";
        };
        Instant since = "month".equals(normalized) ? Instant.now().minus(30, ChronoUnit.DAYS)
                : "week".equals(normalized) ? Instant.now().minus(7, ChronoUnit.DAYS) : Instant.EPOCH;
        String cutoff = "all".equals(normalized) ? "" : " AND s.created_at >= ?";
        String sql = """
                SELECT u.id, u.display_name, u.username, u.avatar_url, u.avatar_color,
                       COUNT(DISTINCT s.problem_id) AS accepted_count, MAX(s.created_at) AS last_accepted_at
                FROM users u JOIN submissions s ON s.user_id = u.id
                WHERE u.enabled = 1 AND s.status = 'AC'%s
                GROUP BY u.id, u.display_name, u.username, u.avatar_url, u.avatar_color
                ORDER BY accepted_count DESC, last_accepted_at ASC, u.id ASC
                LIMIT 100
                """.formatted(cutoff);
        List<Map<String, Object>> rows = "all".equals(normalized)
                ? jdbc.queryForList(sql)
                : jdbc.queryForList(sql, java.sql.Timestamp.from(since));
        List<Entry> entries = new java.util.ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            entries.add(new Entry(((Number) row.get("id")).longValue(), (String) row.get("display_name"),
                    (String) row.get("username"), (String) row.get("avatar_url"), (String) row.get("avatar_color"),
                    ((Number) row.get("accepted_count")).intValue(),
                    ((java.sql.Timestamp) row.get("last_accepted_at")).toInstant(), index + 1));
        }
        Long currentUserId = jwt == null ? null : Long.valueOf(jwt.getSubject());
        Integer currentRank = currentUserId == null ? null : entries.stream()
                .filter(entry -> entry.userId() == currentUserId).map(Entry::rank).findFirst().orElse(null);
        int totalAccepted = entries.stream().mapToInt(Entry::acceptedCount).sum();
        return ApiResponse.ok(new Leaderboard(normalized, "all".equals(normalized) ? null : since, entries,
                totalAccepted, entries.size(), currentUserId, currentRank));
    }
}
