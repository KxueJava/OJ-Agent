package com.codeagentoj.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JudgeWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SandboxRunner sandbox;
    private final Path root;
    private final long timeout;

    public JudgeWorker(JdbcTemplate jdbc, ObjectMapper mapper, SandboxRunner sandbox,
                       @Value("${app.judge.work-dir}") String workDir,
                       @Value("${app.judge.timeout-ms:3000}") long timeout) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.sandbox = sandbox;
        this.root = Path.of(workDir);
        this.timeout = timeout;
    }

    @RabbitListener(queues = "${app.judge.queue:oj.submissions}")
    public void consume(String message) {
        try { judge(mapper.readTree(message).get("submissionId").asLong()); } catch (Exception ignored) { }
    }

    void judge(long id) {
        Map<String, Object> submission = jdbc.queryForList("SELECT id,problem_version_id,language,source_code FROM submissions WHERE id=? AND status='PENDING'", id).stream().findFirst().orElse(null);
        if (submission == null) return;
        jdbc.update("UPDATE submissions SET status='RUNNING',started_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'", id);
        Path directory = null;
        Instant started = Instant.now();
        try {
            Files.createDirectories(root);
            directory = Files.createTempDirectory(root, "submission-");
            LanguageSpec language = LanguageSpec.from(submission.get("language"));
            Files.writeString(directory.resolve(language.sourceFile()), (String) submission.get("source_code"), StandardCharsets.UTF_8);
            // Container startup and javac legitimately take longer than a user program.
            SandboxResult compilation = sandbox.execute(directory, language.compileCommand(), null, Math.max(timeout, 10_000));
            if (compilation.timedOut()) { finish(id, "TLE", "编译超时", elapsed(started), null); return; }
            if (compilation.infrastructureFailure()) { finish(id, "RE", "判题沙盒不可用", elapsed(started), null); return; }
            if (compilation.exitCode() != 0) { finish(id, "CE", compilerSummary(compilation.stderr()), elapsed(started), null); return; }

            List<Map<String, Object>> cases = jdbc.queryForList("SELECT id,input_text,expected_output,visibility FROM test_cases WHERE problem_version_id=? ORDER BY CASE visibility WHEN 'PUBLIC' THEN 0 ELSE 1 END,id", submission.get("problem_version_id"));
            String verdict = "AC";
            String verdictMessage = "全部测试点通过";
            int peakMemoryKb = 0;
            for (Map<String, Object> testCase : cases) {
                Instant caseStarted = Instant.now();
                SandboxResult result = sandbox.execute(directory, language.runCommand(), ((String) testCase.get("input_text")).getBytes(StandardCharsets.UTF_8), timeout);
                peakMemoryKb = Math.max(peakMemoryKb, result.memoryKb());
                String caseVerdict = JudgeVerdict.classify(result.timedOut(), result.memoryExceeded(), result.exitCode(), result.stdout(), (String) testCase.get("expected_output"));
                if (result.infrastructureFailure()) caseVerdict = "RE";
                String output = "HIDDEN".equals(testCase.get("visibility")) ? null : summary(result.stdout());
                jdbc.update("INSERT INTO submission_cases (id,submission_id,test_case_id,verdict,runtime_ms,memory_kb,output_summary) VALUES (?,?,?,?,?,?,?)", nextId(), id, testCase.get("id"), caseVerdict, elapsed(caseStarted), result.memoryKb(), output);
                if (!"AC".equals(caseVerdict)) {
                    verdict = caseVerdict;
                    verdictMessage = failureMessage(caseVerdict, (String) testCase.get("visibility"));
                    break;
                }
            }
            finish(id, verdict, verdictMessage, elapsed(started), peakMemoryKb);
        } catch (Exception ignored) {
            finish(id, "RE", "判题器执行异常", elapsed(started), null);
        } finally {
            if (directory != null) delete(directory);
        }
    }

    private void finish(long id, String verdict, String message, int runtime, Integer memory) { jdbc.update("UPDATE submissions SET status=?,verdict_message=?,runtime_ms=?,memory_kb=?,finished_at=CURRENT_TIMESTAMP WHERE id=?", verdict, message, runtime, memory, id); }
    private int elapsed(Instant start) { return (int) Math.max(1, Duration.between(start, Instant.now()).toMillis()); }
    private String summary(String value) { String text = value == null ? "" : value.trim(); return text.length() > 450 ? text.substring(0, 450) : text; }
    private String compilerSummary(String value) { String text = summary(value); return text.isBlank() ? "编译失败" : text.replaceAll("(?m)^.*Main\\.java(?=:)", "Main.java"); }
    private String failureMessage(String verdict, String visibility) { if ("TLE".equals(verdict)) return "运行超时"; if ("MLE".equals(verdict)) return "内存限制超出"; if ("RE".equals(verdict)) return "判题沙盒执行异常"; return "HIDDEN".equals(visibility) ? "隐藏测试点未通过" : "公开样例输出不匹配"; }
    private long nextId() { return Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)); }
    private void delete(Path path) { try (var paths = Files.walk(path)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(item -> { try { Files.deleteIfExists(item); } catch (IOException ignored) { } }); } catch (IOException ignored) { } }
}
