package com.codeagentoj.server.workspace;

import com.codeagentoj.server.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final JdbcTemplate jdbc; private final RabbitTemplate rabbit;
    public WorkspaceController(JdbcTemplate jdbc, RabbitTemplate rabbit) { this.jdbc=jdbc; this.rabbit=rabbit; }
    public record RunRequest(long problemVersion, @NotBlank String language, @NotBlank @Size(max=100_000) String sourceCode, @Size(max=100_000) String input) {}
    public record RunCase(int order, String verdict, Integer runtimeMs, String outputSummary) {}
    public record RunView(long id, String status, String verdictMessage, Integer runtimeMs, Integer memoryKb, List<RunCase> cases) {}
    public record HistoryItem(long id, String status, String verdictMessage, Integer runtimeMs, Instant createdAt) {}
    @PostMapping("/runs") public ApiResponse<RunView> run(@Valid @RequestBody RunRequest request,@AuthenticationPrincipal Jwt jwt) {
        if (!supportedLanguage(request.language())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"当前仅支持 Java 21、C++17 和 C17");
        long user=user(jwt); Integer valid=jdbc.query("SELECT 1 FROM problem_versions pv JOIN problems p ON p.id=pv.problem_id WHERE pv.id=? AND pv.status='PUBLISHED' AND p.status='PUBLISHED'",(rs,n)->rs.getInt(1),request.problemVersion()).stream().findFirst().orElse(null); if(valid==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"题目版本不可运行"); long id=nextId();jdbc.update("INSERT INTO public_runs (id,user_id,problem_version_id,language,source_code,input_text,status) VALUES (?,?,?,?,?,?, 'PENDING')",id,user,request.problemVersion(),canonicalLanguage(request.language()),request.sourceCode(),request.input());rabbit.convertAndSend("oj.public-runs", "{\"runId\":"+id+"}");return ApiResponse.ok(view(id,user));
    }
    @GetMapping("/runs/{id}") public ApiResponse<RunView> view(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(view(id,user(jwt)));}
    @GetMapping("/history") public ApiResponse<List<HistoryItem>> history(@RequestParam long problemVersion,@AuthenticationPrincipal Jwt jwt){long user=user(jwt);return ApiResponse.ok(jdbc.query("SELECT id,status,verdict_message,runtime_ms,created_at FROM submissions WHERE user_id=? AND problem_version_id=? ORDER BY created_at DESC LIMIT 20",(rs,n)->new HistoryItem(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getObject(4,Integer.class),rs.getTimestamp(5).toInstant()),user,problemVersion));}
    private RunView view(long id,long user){Map<String,Object> run=jdbc.queryForList("SELECT status,verdict_message,runtime_ms,memory_kb FROM public_runs WHERE id=? AND user_id=?",id,user).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"运行记录不存在"));List<RunCase> cases=jdbc.query("SELECT verdict,runtime_ms,output_summary FROM public_run_cases WHERE run_id=? ORDER BY id",(rs,n)->new RunCase(n+1,rs.getString(1),rs.getObject(2,Integer.class),rs.getString(3)),id);return new RunView(id,(String)run.get("status"),(String)run.get("verdict_message"),(Integer)run.get("runtime_ms"),(Integer)run.get("memory_kb"),cases);}
    private boolean supportedLanguage(String language){return "JAVA_21".equals(language)||"Java 21".equals(language)||"CPP_17".equals(language)||"C++17".equals(language)||"C_17".equals(language)||"C17".equals(language);}
    private String canonicalLanguage(String language){if("CPP_17".equals(language)||"C++17".equals(language))return "CPP_17";if("C_17".equals(language)||"C17".equals(language))return "C_17";return "JAVA_21";}
    private long user(Jwt jwt){if(jwt==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"需要登录");return Long.parseLong(jwt.getSubject());} private long nextId(){return Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE));}
}
