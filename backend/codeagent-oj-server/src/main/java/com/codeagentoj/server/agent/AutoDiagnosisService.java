package com.codeagentoj.server.agent;

import com.codeagentoj.server.agent.tools.ToolContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 提交终态自动诊断：闭环的第一半（另一半是前端诊断卡片）。
 *
 * <p>P3 起输出为结构化 {@link Diagnosis}：`findings_json` 存结构化结果，`content` 仍存渲染后的文本，
 * 因此前端可以渲染 `文件:行号`，而不依赖自然语言解析。
 *
 * <p>边界（对齐 project-plan §2「判题结果由 OJ 决定」）：
 * <ul>
 *   <li>只读扫描，只对 <b>非 AC</b> 终态触发，永不修改判题结果；</li>
 *   <li>幂等：{@code agent_findings(submission_id, kind)} 唯一键 + {@code ON DUPLICATE KEY UPDATE}；</li>
 *   <li>成本：同一「用户 + 题目」每 {@code window-seconds} 只自动诊断一次，每轮最多 {@code max-per-scan} 条，
 *       只回看最近 {@code lookback-minutes} 分钟；</li>
 *   <li>隔离：独立线程池 + {@code timeout-seconds} 超时，异常只记日志；</li>
 *   <li>降级：模型不可用/超时/输出未过审核时，落规则化建议并标注 {@code safety_status / output_review}。</li>
 * </ul>
 */
@Service
public class AutoDiagnosisService {
    private static final Logger log = LoggerFactory.getLogger(AutoDiagnosisService.class);
    private static final String KIND = "DIAGNOSIS";

    private final JdbcTemplate jdbc;
    private final AgentOrchestrator orchestrator;
    private final OutputSafety outputSafety;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final int cooldownSeconds;
    private final int maxPerHour;
    private final int timeoutSeconds;
    private final int lookbackMinutes;
    private final int maxPerScan;
    private final String model;
    private final DiagnosisThrottle throttle;
    private final ExecutorService diagnosePool = Executors.newFixedThreadPool(2, daemonFactory("agent-diagnose"));
    private final ExecutorService modelPool = Executors.newFixedThreadPool(4, daemonFactory("agent-model"));

    public AutoDiagnosisService(JdbcTemplate jdbc, AgentOrchestrator orchestrator, OutputSafety outputSafety, ObjectMapper mapper,
                                @Value("${app.agent.auto-diagnose.enabled:true}") boolean enabled,
                                @Value("${app.agent.auto-diagnose.cooldown-seconds:20}") int cooldownSeconds,
                                @Value("${app.agent.auto-diagnose.max-per-hour:20}") int maxPerHour,
                                @Value("${app.agent.auto-diagnose.timeout-seconds:15}") int timeoutSeconds,
                                @Value("${app.agent.auto-diagnose.lookback-minutes:30}") int lookbackMinutes,
                                @Value("${app.agent.auto-diagnose.max-per-scan:3}") int maxPerScan,
                                @Value("${spring.ai.openai.chat.options.model:}") String model) {
        this.jdbc = jdbc;
        this.orchestrator = orchestrator;
        this.outputSafety = outputSafety;
        this.mapper = mapper;
        this.enabled = enabled;
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.maxPerHour = Math.max(1, maxPerHour);
        this.throttle = new DiagnosisThrottle(this.cooldownSeconds, this.maxPerHour);
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.lookbackMinutes = Math.max(1, lookbackMinutes);
        this.maxPerScan = Math.max(1, maxPerScan);
        this.model = model;
    }

    public record DiagnosisView(boolean available, String status, String verdict, String content, String safetyStatus,
                                String findingsJson, String outputReview, String traceJson, Instant createdAt, String reason) {}

    @Scheduled(fixedDelayString = "${app.agent.auto-diagnose.poll-ms:3000}")
    public void scan() {
        if (!enabled) return;
        List<Map<String, Object>> candidates;
        try {
            candidates = jdbc.queryForList("""
                    SELECT s.id,s.user_id,s.problem_version_id,s.language,s.status,s.verdict_message,s.source_code
                      FROM submissions s
                     WHERE s.status IN ('WA','CE','RE','TLE','MLE')
                       AND s.created_at > DATE_SUB(NOW(), INTERVAL ? MINUTE)
                       AND NOT EXISTS (SELECT 1 FROM agent_findings f WHERE f.submission_id=s.id AND f.kind='DIAGNOSIS')
                     ORDER BY s.created_at DESC, s.id DESC
                     LIMIT ?
                    """, lookbackMinutes, maxPerScan);
        } catch (RuntimeException error) {
            log.warn("agent auto-diagnose scan skipped: {}", error.getMessage());
            return;
        }
        for (Map<String, Object> candidate : candidates) {
            long userId = number(candidate.get("user_id"));
            long version = number(candidate.get("problem_version_id"));
            long now = System.currentTimeMillis();
            if (throttle.check(userId, version, now) != DiagnosisThrottle.Decision.ALLOW) continue;
            throttle.record(userId, version, now);
            diagnosePool.submit(() -> diagnose(candidate));
        }
    }

    /** 前端查询用：返回本次提交的诊断（不存在或不属于该用户时 available=false）。 */
    public DiagnosisView view(long submissionId, long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT f.verdict,f.content,f.safety_status,f.findings_json,f.output_review,f.trace_json,f.created_at FROM agent_findings f JOIN submissions s ON s.id=f.submission_id WHERE f.submission_id=? AND f.kind='DIAGNOSIS' AND s.user_id=?",
                submissionId, userId);
        if (rows.isEmpty()) return pendingView(submissionId, userId);
        Map<String, Object> row = rows.get(0);
        Object createdAt = row.get("created_at");
        return new DiagnosisView(true, "READY", (String) row.get("verdict"), (String) row.get("content"),
                (String) row.get("safety_status"), (String) row.get("findings_json"), (String) row.get("output_review"),
                (String) row.get("trace_json"),
                createdAt instanceof java.sql.Timestamp timestamp ? timestamp.toInstant() : Instant.now(), null);
    }

    /**
     * 还没有诊断时，明确告诉前端"为什么没有"。
     * 静默无响应是最糟的体验：用户分不清是排队中、已通过、还是被额度拦住。
     */
    private DiagnosisView pendingView(long submissionId, long userId) {
        List<Map<String, Object>> submissions = jdbc.queryForList(
                "SELECT user_id,problem_version_id,status FROM submissions WHERE id=? AND user_id=?", submissionId, userId);
        if (submissions.isEmpty()) {
            return new DiagnosisView(false, "NONE", null, null, null, null, null, null, null, "提交不存在或不属于当前用户");
        }
        Map<String, Object> submission = submissions.get(0);
        String status = (String) submission.get("status");
        if ("AC".equals(status)) {
            return new DiagnosisView(false, "NONE", null, null, null, null, null, null, null, "已通过，无需诊断");
        }
        if (!List.of("WA", "CE", "RE", "TLE", "MLE").contains(status)) {
            return new DiagnosisView(false, "PENDING", null, null, null, null, null, null, null, "判题进行中，完成后会自动诊断");
        }
        DiagnosisThrottle.Decision decision = throttle.check(number(submission.get("user_id")),
                number(submission.get("problem_version_id")), System.currentTimeMillis());
        String reason = switch (decision) {
            case COOLDOWN -> "同一道题 " + cooldownSeconds + " 秒内只自动诊断一次，稍等即可，也可以在 Agent 面板手动提问";
            case HOURLY_CAP -> "本小时自动诊断额度已用完（上限 " + maxPerHour + " 次/小时），可在 Agent 面板手动提问";
            case ALLOW -> "已排队，数秒后自动生成";
        };
        return new DiagnosisView(false, decision == DiagnosisThrottle.Decision.ALLOW ? "PENDING" : "SKIPPED",
                null, null, null, null, null, null, null, reason);
    }

    private void diagnose(Map<String, Object> submission) {
        long submissionId = number(submission.get("id"));
        long userId = number(submission.get("user_id"));
        long version = number(submission.get("problem_version_id"));
        String verdict = (String) submission.get("status");
        long started = System.nanoTime();
        String content;
        String safety;
        String findingsJson = null;

        Diagnosis structured = null;
        List<String> trace = List.of();
        try {
            Map<String, Object> toolContext = Map.<String, Object>of(
                    ToolContextKeys.USER_ID, userId, ToolContextKeys.PROBLEM_VERSION, version);
            String question = userPrompt(submission);
            boolean hasSource = submission.get("source_code") instanceof String text && !text.isBlank();
            Future<AgentOrchestrator.Investigation> future = modelPool.submit(
                    () -> orchestrator.investigate(question, verdict, hasSource, toolContext));
            AgentOrchestrator.Investigation investigation = future.get(timeoutSeconds * 3L, TimeUnit.SECONDS);
            if (investigation != null && investigation.diagnosis() != null) {
                structured = investigation.diagnosis();
                trace = investigation.trace();
            }
        } catch (TimeoutException timeout) {
            log.warn("agent multi-agent diagnosis timed out for submission {}", submissionId);
        } catch (Exception error) {
            log.warn("agent multi-agent diagnosis failed for submission {}: {}", submissionId, error.getMessage());
        }

        if (structured == null) {
            content = fallback(verdict, (String) submission.get("verdict_message"));
            safety = "FALLBACK";
        } else {
            content = structured.render();
            findingsJson = json(structured.findings());
            safety = "PASSED";
        }

        OutputSafety.Review review = outputSafety.review(content);
        String outputReview = review.status();
        if (review.blocked()) {
            content = outputSafety.safeTemplate(review.reason());
            findingsJson = null;
        }
        save(submissionId, userId, version, verdict, content, findingsJson, safety, outputReview, json(trace),
                (System.nanoTime() - started) / 1_000_000L);
    }

    private void save(long submissionId, long userId, long version, String verdict, String content, String findingsJson,
                      String safety, String outputReview, String traceJson, long latencyMs) {
        try {
            jdbc.update("""
                    INSERT INTO agent_findings (id,submission_id,session_id,user_id,problem_version_id,kind,verdict,summary,content,findings_json,trace_json,model,latency_ms,safety_status,output_review)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE content=VALUES(content), summary=VALUES(summary), findings_json=VALUES(findings_json),
                                            trace_json=VALUES(trace_json), latency_ms=VALUES(latency_ms),
                                            safety_status=VALUES(safety_status), output_review=VALUES(output_review)
                    """, id(), submissionId, null, userId, version, KIND, verdict, summary(content), content, findingsJson,
                    traceJson, model, (int) latencyMs, safety, outputReview);
        } catch (RuntimeException error) {
            log.warn("agent auto-diagnose could not save finding for submission {}: {}", submissionId, error.getMessage());
        }
    }

    private String userPrompt(Map<String, Object> submission) {
        String code = submission.get("source_code") instanceof String text ? text : "";
        if (code.length() > 6000) code = code.substring(0, 6000) + "\n…（代码已截断）";
        return "提交 ID：" + submission.get("id")
                + "\n判题状态：" + submission.get("status")
                + "\n判题说明：" + (submission.get("verdict_message") == null ? "-" : submission.get("verdict_message"))
                + "\n语言：" + submission.get("language")
                + "\n用户本次提交的源码（含行号）：\n" + numbered(code) + "\n请给出诊断。";
    }

    /** 给源码加上行号，模型才能给出可跳转的 line。 */
    private static String numbered(String code) {
        StringBuilder text = new StringBuilder("```\n");
        String[] lines = code.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            text.append(index + 1).append(" | ").append(lines[index]).append('\n');
        }
        return text.append("```").toString();
    }

    private String fallback(String verdict, String verdictMessage) {
        return "本次提交未通过（" + verdict + (verdictMessage == null || verdictMessage.isBlank() ? "" : "：" + verdictMessage) + "）。"
                + "自动诊断依赖模型服务，当前不可用（或超时），先给出规则化排查清单：\n"
                + "1. 输入解析：确认按题面格式读取，注意空格、换行与多组数据；\n"
                + "2. 边界条件：负数、0、单个元素、极值、空输入；\n"
                + "3. 输出格式：大小写、是否多余空行、是否需要换行结尾。\n"
                + "可以在 Agent 面板里手动提问，稍后重试自动诊断。";
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            log.warn("agent auto-diagnose could not serialize findings: {}", error.getMessage());
            return null;
        }
    }

    private static String summary(String content) {
        if (content == null) return null;
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 500 ? flat : flat.substring(0, 500);
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static long id() {
        return Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** 仅用于排障：最近一次节流时间。 */
    Optional<Long> lastThrottleAt(long userId, long version) {
        return Optional.empty();
    }
}
