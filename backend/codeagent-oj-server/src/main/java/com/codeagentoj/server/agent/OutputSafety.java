package com.codeagentoj.server.agent;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 输出侧 Safety：与输入侧 {@link AgentPolicy} 独立的一道审核，作用于模型产出。
 *
 * <p>用确定性规则而不是再一次模型调用，理由是：可单测、零额外延迟与成本、结论可回放。
 * 规则覆盖两类真正危险的产出：完整可提交代码、隐藏用例数据。
 * 若后续要升级为模型审核，只要替换 {@link #review(String)} 的实现，调用方无需改动。
 */
@Component
public class OutputSafety {
    static final Pattern FULL_SOLUTION = Pattern.compile("(?s)```[a-zA-Z]*\\s*(?:public\\s+)?class\\s+Main\\b.*?```");
    static final Pattern HIDDEN_LEAK = Pattern.compile("(?i)(?:隐藏用例|hidden\\s+(?:test|case))[^。\\n]{0,24}(?:输入|期望输出|input|expected)");

    public record Review(String status, String reason) {
        public boolean blocked() { return "BLOCKED".equals(status); }
    }

    public Review review(String content) {
        if (content == null || content.isBlank()) return new Review("BLOCKED", "输出为空");
        if (FULL_SOLUTION.matcher(content).find()) return new Review("BLOCKED", "包含完整可提交代码");
        if (HIDDEN_LEAK.matcher(content).find()) return new Review("BLOCKED", "疑似透露隐藏用例数据");
        return new Review("PASSED", null);
    }

    /** 被拦截时替换成的安全话术：保留诊断价值，去掉违规内容。 */
    public String safeTemplate(String reason) {
        return "本次自动诊断的原始输出未通过输出侧审核（" + reason + "），已按策略替换。\n"
                + "可以先按这三步自查：\n"
                + "1. 输入解析是否符合题面格式；\n"
                + "2. 边界条件（负数、0、极值、空输入）；\n"
                + "3. 输出格式是否与示例完全一致。\n"
                + "需要更具体的定位，请在 Agent 面板里追问。";
    }
}
