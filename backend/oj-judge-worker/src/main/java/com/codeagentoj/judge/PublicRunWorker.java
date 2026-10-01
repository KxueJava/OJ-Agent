package com.codeagentoj.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Configuration
class PublicRunQueueConfig { @Bean Queue publicRunsQueue() { return new Queue("oj.public-runs", true); } }

@Component
public class PublicRunWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SandboxRunner sandbox;

    PublicRunWorker(JdbcTemplate jdbc, ObjectMapper mapper, SandboxRunner sandbox) { this.jdbc = jdbc; this.mapper = mapper; this.sandbox = sandbox; }

    @RabbitListener(queues = "oj.public-runs")
    public void consume(String message) { try { judge(mapper.readTree(message).get("runId").asLong()); } catch (Exception ignored) { } }

    private void judge(long id) {
        Map<String, Object> run = jdbc.queryForList("SELECT problem_version_id,language,source_code,input_text FROM public_runs WHERE id=? AND status='PENDING'", id).stream().findFirst().orElse(null);
        if (run == null) return;
        jdbc.update("UPDATE public_runs SET status='RUNNING' WHERE id=?", id);
        Path directory = null;
        Instant started = Instant.now();
        int peakMemoryKb = 0;
        try {
            directory = Files.createTempDirectory("oj-public-run-");
            LanguageSpec language = LanguageSpec.from(run.get("language"));
            Files.writeString(directory.resolve(language.sourceFile()), (String) run.get("source_code"), StandardCharsets.UTF_8);
            // Container startup and javac legitimately take longer than a user program.
            SandboxResult compilation = sandbox.execute(directory, language.compileCommand(), null, 10_000);
            if (compilation.timedOut()) { finish(id, "TLE", "编译超时", ms(started)); return; }
            if (compilation.infrastructureFailure()) { finish(id, "RE", sandboxMessage(compilation.stderr()), ms(started)); return; }
            if (compilation.exitCode() != 0) { finish(id, "CE", "编译失败", ms(started)); return; }
            String customInput = (String) run.get("input_text");
            if (customInput != null) {
                Map<String, Object> example = jdbc.queryForList("SELECT id,output_text FROM examples WHERE problem_version_id=? AND input_text=? ORDER BY display_order LIMIT 1", run.get("problem_version_id"), customInput).stream().findFirst().orElse(null);
                Instant sampleStarted = Instant.now();
                SandboxResult result = sandbox.execute(directory, language.runCommand(), customInput.getBytes(StandardCharsets.UTF_8), 3000);
                String verdict = example == null
                        ? executionVerdict(result)
                        : JudgeVerdict.classify(result.timedOut(), result.memoryExceeded(), result.exitCode(), result.stdout(), (String) example.get("output_text"));
                String output = result.stdout().isBlank() && !result.stderr().isBlank() ? result.stderr() : result.stdout();
                jdbc.update("INSERT INTO public_run_cases (id,run_id,example_id,verdict,runtime_ms,output_summary) VALUES (?,?,?,?,?,?)", nextId(), id, example == null ? null : example.get("id"), verdict, ms(sampleStarted), summary(output));
                String verdictMessage = example == null && "EXECUTED".equals(verdict)
                        ? "自定义输入执行完成"
                        : verdict.equals("AC") ? "公开样例通过" : message(verdict);
                finish(id, verdict, verdictMessage, ms(started), result.memoryKb());
                return;
            }
            List<Map<String, Object>> examples = jdbc.queryForList("SELECT id,input_text,output_text FROM examples WHERE problem_version_id=? ORDER BY display_order", run.get("problem_version_id"));
            for (Map<String, Object> sample : examples) {
                Instant sampleStarted = Instant.now();
                SandboxResult result = sandbox.execute(directory, language.runCommand(), ((String) sample.get("input_text")).getBytes(StandardCharsets.UTF_8), 3000);
                peakMemoryKb = Math.max(peakMemoryKb, result.memoryKb());
                String verdict = JudgeVerdict.classify(result.timedOut(), result.memoryExceeded(), result.exitCode(), result.stdout(), (String) sample.get("output_text"));
                if (result.infrastructureFailure()) {
                    jdbc.update("INSERT INTO public_run_cases (id,run_id,example_id,verdict,runtime_ms,output_summary) VALUES (?,?,?,?,?,?)", nextId(), id, sample.get("id"), "RE", ms(sampleStarted), summary(result.stderr()));
                    finish(id, "RE", sandboxMessage(result.stderr()), ms(started));
                    return;
                }
                String visibleOutput = result.stdout().isBlank() && !result.stderr().isBlank()
                        ? result.stderr()
                        : result.stdout();
                jdbc.update("INSERT INTO public_run_cases (id,run_id,example_id,verdict,runtime_ms,output_summary) VALUES (?,?,?,?,?,?)", nextId(), id, sample.get("id"), verdict, ms(sampleStarted), summary(visibleOutput));
                if (!"AC".equals(verdict)) { finish(id, verdict, message(verdict), ms(started), peakMemoryKb); return; }
            }
            finish(id, "AC", "公开样例全部通过", ms(started), peakMemoryKb);
        } catch (Exception ignored) {
            finish(id, "RE", "运行服务异常", ms(started), peakMemoryKb);
        } finally {
            if (directory != null) delete(directory);
        }
    }

    private void finish(long id, String status, String message, int runtime) { finish(id, status, message, runtime, 0); }
    private void finish(long id, String status, String message, int runtime, int memoryKb) { jdbc.update("UPDATE public_runs SET status=?,verdict_message=?,runtime_ms=?,memory_kb=?,finished_at=CURRENT_TIMESTAMP WHERE id=?", status, message, runtime, memoryKb, id); }
    private int ms(Instant start) { return (int) Math.max(1, Duration.between(start, Instant.now()).toMillis()); }
    private String summary(String value) { String text = value == null ? "" : value.trim(); return text.length() > 450 ? text.substring(0, 450) : text; }
    private String message(String verdict) { return switch (verdict) { case "TLE" -> "运行超时"; case "MLE" -> "内存限制超出"; case "WA" -> "公开样例输出不匹配"; default -> "判题沙盒执行异常"; }; }
    private String executionVerdict(SandboxResult result) {
        if (result.timedOut()) return "TLE";
        if (result.memoryExceeded()) return "MLE";
        if (result.exitCode() != 0) return "RE";
        return "EXECUTED";
    }
    private String sandboxMessage(String stderr) { String detail = summary(stderr); return detail.isBlank() ? "判题沙盒不可用" : "判题沙盒不可用：" + detail; }
    private long nextId() { return Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)); }
    private void delete(Path path) { try (var paths = Files.walk(path)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(item -> { try { Files.deleteIfExists(item); } catch (Exception ignored) { } }); } catch (Exception ignored) { } }
}
