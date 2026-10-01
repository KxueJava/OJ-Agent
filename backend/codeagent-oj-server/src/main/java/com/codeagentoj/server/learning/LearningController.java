package com.codeagentoj.server.learning;

import com.codeagentoj.server.api.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/learning")
public class LearningController {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public LearningController(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public record Mistake(long id, String slug, String title, String difficulty, String errorType, String diagnosis, String reviewNote, String status, String createdAt, long submissionId) {}
    public record Recommendation(String slug, String title, String difficulty, String topic, String reason) {}
    public record Plan(String date, int targetCount, int completedCount, List<Recommendation> recommendations) {}
    public record Overview(int openMistakes, int reviewedMistakes, int totalEvents, Plan today, List<Mistake> mistakes, List<Recommendation> recommendations) {}

    @GetMapping("/overview")
    public ApiResponse<Overview> overview(@AuthenticationPrincipal Jwt jwt) {
        long user = user(jwt); syncMistakes(user); ensurePlan(user);
        List<Mistake> mistakes = mistakes(user);
        Plan plan = plan(user);
        int open = (int) mistakes.stream().filter(m -> "OPEN".equals(m.status())).count();
        int reviewed = mistakes.size() - open;
        int events = jdbc.queryForObject("SELECT COUNT(*) FROM learning_events WHERE user_id=?", Integer.class, user);
        return ApiResponse.ok(new Overview(open, reviewed, events, plan, mistakes, plan.recommendations()));
    }

    @GetMapping("/mistakes") public ApiResponse<List<Mistake>> mistakesApi(@AuthenticationPrincipal Jwt jwt) { long user=user(jwt); syncMistakes(user); return ApiResponse.ok(mistakes(user)); }

    @PutMapping("/mistakes/{id}/review")
    public ApiResponse<Void> review(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { jdbc.update("UPDATE mistake_books SET status='REVIEWED', reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?", id, user(jwt)); return ApiResponse.ok(null); }

    @GetMapping("/plan") public ApiResponse<Plan> planApi(@AuthenticationPrincipal Jwt jwt) { long user=user(jwt); ensurePlan(user); return ApiResponse.ok(plan(user)); }

    private void syncMistakes(long user) {
        List<Map<String,Object>> failed = jdbc.queryForList("""
                SELECT s.id,s.problem_id,s.status,s.verdict_message,s.created_at,p.title
                FROM submissions s JOIN problems p ON p.id=s.problem_id
                WHERE s.user_id=? AND s.status IN ('WA','CE','RE','TLE','MLE')
                  AND NOT EXISTS (SELECT 1 FROM mistake_books m WHERE m.user_id=s.user_id AND m.submission_id=s.id)
                ORDER BY s.created_at DESC LIMIT 100
                """, user);
        for (Map<String,Object> row : failed) {
            long submission=((Number)row.get("id")).longValue(); String status=(String)row.get("status");
            String diagnosis = diagnosis(status, row.get("verdict_message"));
            jdbc.update("INSERT IGNORE INTO mistake_books (id,user_id,problem_id,submission_id,error_type,diagnosis,review_note) VALUES (?,?,?,?,?,?,?)", id(), user, row.get("problem_id"), submission, status, diagnosis, review(status));
            try { jdbc.update("INSERT INTO learning_events (id,user_id,problem_id,submission_id,event_type,payload_json) VALUES (?,?,?,?,?,?)", id(), user, row.get("problem_id"), submission, "MISTAKE_CAPTURED", mapper.writeValueAsString(Map.of("status", status))); } catch (JsonProcessingException ignored) { }
        }
    }

    private List<Mistake> mistakes(long user) { return jdbc.query("""
            SELECT m.id,p.slug,p.title,p.difficulty,m.error_type,m.diagnosis,m.review_note,m.status,m.created_at,m.submission_id
            FROM mistake_books m JOIN problems p ON p.id=m.problem_id WHERE m.user_id=? ORDER BY m.created_at DESC LIMIT 50
            """, (rs,n)->new Mistake(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant().toString(),rs.getLong(10)), user); }

    private void ensurePlan(long user) { if (jdbc.queryForObject("SELECT COUNT(*) FROM study_plans WHERE user_id=? AND plan_date=CURRENT_DATE", Integer.class, user)==0) { List<Recommendation> recs = recommendations(user); try { jdbc.update("INSERT INTO study_plans (id,user_id,plan_date,target_count,completed_count,recommendation_json) VALUES (?,?,CURRENT_DATE,?,?,?)", id(),user,3,0,mapper.writeValueAsString(recs)); } catch (JsonProcessingException ignored) { } } }
    private Plan plan(long user) { Map<String,Object> row=jdbc.queryForList("SELECT plan_date,target_count,completed_count,recommendation_json FROM study_plans WHERE user_id=? AND plan_date=CURRENT_DATE",user).stream().findFirst().orElseThrow(); return new Plan(row.get("plan_date").toString(),((Number)row.get("target_count")).intValue(),((Number)row.get("completed_count")).intValue(),recommendationsFrom(row.get("recommendation_json"))); }
    private List<Recommendation> recommendations(long user) { return jdbc.query("""
            SELECT p.slug,p.title,p.difficulty,CASE WHEN p.difficulty='EASY' THEN '基础练习' ELSE '算法练习' END topic,
            CASE WHEN EXISTS (SELECT 1 FROM mistake_books m WHERE m.user_id=? AND m.problem_id=p.id AND m.status='OPEN') THEN '复习你的错题' ELSE '扩展训练覆盖面' END reason
            FROM problems p WHERE p.status='PUBLISHED' AND NOT EXISTS (SELECT 1 FROM submissions s WHERE s.user_id=? AND s.problem_id=p.id AND s.status='AC') ORDER BY p.difficulty='EASY' DESC,p.updated_at DESC LIMIT 3
            """, (rs,n)->new Recommendation(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)), user,user); }
    private List<Recommendation> recommendationsFrom(Object json) { if (json==null) return List.of(); try { return mapper.readValue(json.toString(), mapper.getTypeFactory().constructCollectionType(List.class, Recommendation.class)); } catch (Exception e) { return List.of(); } }
    private String diagnosis(String status,Object message) { return switch(status) { case "WA" -> "输出与预期结果不一致，重点复核边界条件、匹配规则和输出格式。"; case "CE" -> "代码未通过编译，先按编译器报错位置修复语法或类型问题。"; case "RE" -> "运行时异常，检查输入读取、空值和数组下标边界。"; case "TLE" -> "运行超时，检查是否存在重复扫描或不必要的高复杂度操作。"; case "MLE" -> "内存超限，检查集合规模和是否保留了不必要的数据。"; default -> message==null?"需要结合提交结果复盘。":message.toString(); }; }
    private String review(String status) { return switch(status) { case "WA" -> "复盘：构造最小反例，逐步核对状态变化。"; case "CE" -> "复盘：记录报错类型和对应 Java 语法规则。"; case "TLE" -> "复盘：写出时间复杂度并寻找重复工作。"; default -> "复盘：确认异常触发条件和边界输入。"; }; }
    private long user(Jwt jwt) { if (jwt==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请先登录"); return Long.parseLong(jwt.getSubject()); }
    private long id(){return Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE));}
}
