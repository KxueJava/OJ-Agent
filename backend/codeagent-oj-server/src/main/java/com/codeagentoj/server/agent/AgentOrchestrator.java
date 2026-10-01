package com.codeagentoj.server.agent;

import com.codeagentoj.server.agent.tools.ProblemTools;
import com.codeagentoj.server.agent.tools.SubmissionTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 多 Agent 编排（project-plan §8 的落地版）：Supervisor → 专职 Agent（并行）→ Safety → Finalizer。
 *
 * <p>与文档一致的约束：
 * <ul>
 *   <li>每轮最多调用 2 个业务 Agent（文档上限 3）+ 1 次 Finalizer；</li>
 *   <li>专职 Agent 各自拥有独立系统提示、输出契约（{@link Diagnosis}）与工具子集，不是同一个提示词的多种模式；</li>
 *   <li>Safety 在 Finalizer 之后对最终产物做输出侧审核，结论不能被其他 Agent 覆盖；</li>
 *   <li>共享的是结构化结论，不共享隐藏测试、标准答案或原始推理。</li>
 * </ul>
 *
 * <p>用于后台自动诊断而不是交互问答：多 Agent 必须等所有专家返回才能汇总，
 * 会牺牲首 token 延迟；交互路径因此保持单 Agent 直出。
 */
@Service
public class AgentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final String RULES = "规则：只基于题面公开信息与用户自己的提交记录；不要猜测或索要隐藏用例的输入与期望输出；"
            + "不要给出完整可提交代码；中文回答，findings 控制在 3 条以内。"
            + "按结构化字段回答：summary 一句话结论；findings 每项含 file（如 Main.java）、line（对应带行号的源码）、"
            + "severity（error/warning/info）、message、suggestion。";

    /** 专职 Agent 定义：独立职责、独立提示词、独立工具子集。 */
    public enum Specialist {
        DEBUGGER("Debugger", "定位失败原因", new Object[]{}),
        REVIEWER("Reviewer", "正确性与边界审查", new Object[]{});

        private final String label;
        private final String duty;
        private final Object[] tools;

        Specialist(String label, String duty, Object[] tools) {
            this.label = label;
            this.duty = duty;
            this.tools = tools;
        }

        public String label() { return label; }
        public String duty() { return duty; }
        Object[] tools() { return tools; }
    }

    public record Plan(List<Specialist> specialists, String reason) {}
    public record SpecialistResult(Diagnosis diagnosis, long latencyMs, boolean ok) {}
    public record Investigation(Diagnosis diagnosis, List<String> trace, long latencyMs) {}

    private final DeepSeekClient deepSeek;
    private final OutputSafety outputSafety;
    private final ObjectMapper mapper;
    private final ProblemTools problemTools;
    private final SubmissionTools submissionTools;
    private final int timeoutSeconds;
    private final ExecutorService pool = Executors.newFixedThreadPool(4, daemonFactory("agent-specialist"));

    public AgentOrchestrator(DeepSeekClient deepSeek, OutputSafety outputSafety, ObjectMapper mapper,
                             ProblemTools problemTools, SubmissionTools submissionTools,
                             @Value("${app.agent.auto-diagnose.timeout-seconds:15}") int timeoutSeconds) {
        this.deepSeek = deepSeek;
        this.outputSafety = outputSafety;
        this.mapper = mapper;
        this.problemTools = problemTools;
        this.submissionTools = submissionTools;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    /**
     * Supervisor 规划。当前实现是确定性规则（可单测、零额外延迟），保证：
     * 编译错误只找 Debugger；运行期失败让 Debugger 与 Reviewer 并行；没有源码就不派专家。
     */
    static Plan plan(String verdict, boolean hasSourceCode) {
        if (!hasSourceCode) return new Plan(List.of(), "没有源码可分析");
        if ("CE".equals(verdict)) return new Plan(List.of(Specialist.DEBUGGER), "编译错误只需要定位编译问题");
        return new Plan(List.of(Specialist.DEBUGGER, Specialist.REVIEWER), "运行期失败：定位与审查并行");
    }

    public Investigation investigate(String question, String verdict, boolean hasSourceCode, Map<String, Object> toolContext) {
        long started = System.nanoTime();
        Plan plan = plan(verdict, hasSourceCode);
        List<String> trace = new ArrayList<>();
        trace.add("Supervisor(plan=" + plan.specialists().stream().map(Specialist::label).collect(Collectors.joining("+"))
                + ", reason=" + plan.reason() + ")");
        if (plan.specialists().isEmpty()) return new Investigation(null, trace, elapsed(started));

        List<Future<SpecialistResult>> futures = new ArrayList<>();
        for (Specialist specialist : plan.specialists()) {
            futures.add(pool.submit(() -> runSpecialist(specialist, question, toolContext)));
        }

        List<Diagnosis> conclusions = new ArrayList<>();
        for (int index = 0; index < futures.size(); index++) {
            Specialist specialist = plan.specialists().get(index);
            try {
                SpecialistResult result = futures.get(index).get(timeoutSeconds, TimeUnit.SECONDS);
                trace.add(specialist.label() + "(" + (result.ok() ? "ok" : "blocked-or-failed") + "," + result.latencyMs() + "ms)");
                if (result.ok() && result.diagnosis() != null) conclusions.add(result.diagnosis());
            } catch (Exception error) {
                trace.add(specialist.label() + "(timeout)");
                log.warn("specialist {} did not finish: {}", specialist.label(), error.getMessage());
            }
        }
        if (conclusions.isEmpty()) return new Investigation(null, trace, elapsed(started));

        Diagnosis merged = conclusions.size() == 1 ? conclusions.get(0) : finalize(conclusions, trace);
        OutputSafety.Review review = outputSafety.review(merged.render());
        trace.add("Safety(" + review.status() + ")");
        if (review.blocked()) {
            trace.add("Finalizer(replaced-by-safe-template)");
            merged = new Diagnosis(outputSafety.safeTemplate(review.reason()), List.of());
        }
        return new Investigation(merged, trace, elapsed(started));
    }

    private SpecialistResult runSpecialist(Specialist specialist, String question, Map<String, Object> toolContext) {
        long started = System.nanoTime();
        try {
            Diagnosis diagnosis = deepSeek.entity(systemPrompt(specialist), question, toolsFor(specialist), toolContext, Diagnosis.class);
            if (outputSafety.review(diagnosis.render()).blocked()) {
                log.warn("specialist {} output blocked by safety rules", specialist.label());
                return new SpecialistResult(null, elapsed(started), false);
            }
            return new SpecialistResult(diagnosis, elapsed(started), true);
        } catch (RuntimeException error) {
            log.warn("specialist {} failed: {}", specialist.label(), error.getMessage());
            return new SpecialistResult(null, elapsed(started), false);
        }
    }

    private Diagnosis finalize(List<Diagnosis> conclusions, List<String> trace) {
        long started = System.nanoTime();
        try {
            String payload = mapper.writeValueAsString(conclusions);
            Diagnosis merged = deepSeek.entity(
                    "你是 CodeAgent OJ 的 Finalizer。把多位专家的结构化诊断合并成一份最终诊断：去重、按严重程度排序、最多保留 3 条 findings，"
                            + "summary 用一句话说清这次提交为什么没通过。只合并专家给出的结论，不新增猜测。" + RULES,
                    "各专家的结构化诊断（JSON 数组）：\n" + payload, null, null, Diagnosis.class);
            trace.add("Finalizer(ok," + elapsed(started) + "ms)");
            return merged;
        } catch (Exception error) {
            trace.add("Finalizer(failed)");
            log.warn("finalizer failed, falling back to the first specialist conclusion: {}", error.getMessage());
            return conclusions.get(0);
        }
    }

    private Object[] toolsFor(Specialist specialist) {
        return specialist == Specialist.DEBUGGER
                ? new Object[]{problemTools, submissionTools}   // 需要看提交结果
                : new Object[]{problemTools};                   // 只审查代码本身
    }

    private String systemPrompt(Specialist specialist) {
        return "你是 CodeAgent OJ 的 " + specialist.label() + "。" + specialist.duty() + "。" + RULES;
    }

    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
