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

    /**
     * 判题失败归类。
     * kind=USER 表示用户代码的问题；INFRA/INTERNAL 表示判题侧的问题 —— 界面必须能区分，
     * 否则沙盒挂了也会显示成"运行错误"，误导做题的人去改代码。
     */
    record Failure(String kind, String message) {}

    static Failure classify(String verdict, boolean infrastructure, String visibility) {
        if (infrastructure) return new Failure("INFRA", "判题基础设施异常（沙盒不可用），与你的代码无关");
        if ("AC".equals(verdict)) return new Failure(null, "全部测试点通过");
        if ("TLE".equals(verdict)) return new Failure("USER", "运行超时（超过时限）");
        if ("MLE".equals(verdict)) return new Failure("USER", "内存超限");
        if ("RE".equals(verdict)) return new Failure("USER", "运行期错误（程序非正常退出）");
        if ("CE".equals(verdict)) return new Failure("USER", "编译错误");
        return new Failure("USER", "HIDDEN".equals(visibility) ? "隐藏测试点未通过" : "公开样例输出不匹配");
    }

    void judge(long id) {
        Map<String, Object> submission = jdbc.queryForList("SELECT id,problem_version_id,language,source_code FROM submissions WHERE id=? AND status='PENDING'", id).stream().findFirst().orElse(null);
        if (submission == null) return;
        jdbc.update("UPDATE submissions SET status='RUNNING',started_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'", id);
        Path directory = null;
        Instant started = Instant.now();
        Integer compileMs = null;
        try {
            Files.createDirectories(root);
            directory = Files.createTempDirectory(root, "submission-");
            LanguageSpec language = LanguageSpec.from(submission.get("language"));
            Files.writeString(directory.resolve(language.sourceFile()), (String) submission.get("source_code"), StandardCharsets.UTF_8);
            // Container startup and javac legitimately take longer than a user program.
            Instant compileStarted = Instant.now();
            SandboxResult compilation = sandbox.execute(directory, language.compileCommand(), null, Math.max(timeout, 10_000));
            compileMs = elapsed(compileStarted);
            if (compilation.timedOut()) { finish(id, "TLE", classify("TLE", false, null), elapsed(started), null, compileMs); return; }
            if (compilation.infrastructureFailure()) { finish(id, "RE", classify("RE", true, null), elapsed(started), null, compileMs); return; }
            if (compilation.exitCode() != 0) { finish(id, "CE", new Failure("USER", compilerSummary(compilation.stderr())), elapsed(started), null, compileMs); return; }

            List<Map<String, Object>> cases = jdbc.queryForList("SELECT id,input_text,expected_output,visibility FROM test_cases WHERE problem_version_id=? ORDER BY CASE visibility WHEN 'PUBLIC' THEN 0 ELSE 1 END,id", submission.get("problem_version_id"));
            String verdict = "AC";
            String visibility = null;
            boolean infrastructure = false;
            int peakMemoryKb = 0;
            for (Map<String, Object> testCase : cases) {
                Instant caseStarted = Instant.now();
                SandboxResult result = sandbox.execute(directory, language.runCommand(), ((String) testCase.get("input_text")).getBytes(StandardCharsets.UTF_8), timeout);
                peakMemoryKb = Math.max(peakMemoryKb, result.memoryKb());
                String caseVerdict = JudgeVerdict.classify(result.timedOut(), result.memoryExceeded(), result.exitCode(), result.stdout(), (String) testCase.get("expected_output"));
                if (result.infrastructureFailure()) { caseVerdict = "RE"; infrastructure = true; }
                String output = "HIDDEN".equals(testCase.get("visibility")) ? null : summary(result.stdout());
                jdbc.update("INSERT INTO submission_cases (id,submission_id,test_case_id,verdict,runtime_ms,memory_kb,output_summary) VALUES (?,?,?,?,?,?,?)", nextId(), id, testCase.get("id"), caseVerdict, elapsed(caseStarted), result.memoryKb(), output);
                if (!"AC".equals(caseVerdict)) {
                    verdict = caseVerdict;
                    visibility = (String) testCase.get("visibility");
                    break;
                }
            }
            finish(id, verdict, classify(verdict, infrastructure, visibility), elapsed(started), peakMemoryKb, compileMs);
        } catch (Exception ignored) {
            finish(id, "RE", new Failure("INTERNAL", "判题器内部异常，请稍后重试"), elapsed(started), null, compileMs);
        } finally {
            if (directory != null) delete(directory);
        }
    }

    private void finish(long id, String verdict, Failure failure, int runtime, Integer memory, Integer compileMs) {
        jdbc.update("UPDATE submissions SET status=?,verdict_message=?,failure_kind=?,runtime_ms=?,compile_ms=?,memory_kb=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",
                verdict, failure.message(), failure.kind(), runtime, compileMs, memory, id);
    }

    private int elapsed(Instant start) { return (int) Math.max(1, Duration.between(start, Instant.now()).toMillis()); }
    private String summary(String value) { String text = value == null ? "" : value.trim(); return text.length() > 450 ? text.substring(0, 450) : text; }
    private String compilerSummary(String value) { String text = summary(value); return text.isBlank() ? "编译失败" : text.replaceAll("(?m)^.*Main\\.java(?=:)", "Main.java"); }
    private long nextId() { return Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)); }
    private void delete(Path path) { try (var paths = Files.walk(path)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(item -> { try { Files.deleteIfExists(item); } catch (IOException ignored) { } }); } catch (IOException ignored) { } }
}
