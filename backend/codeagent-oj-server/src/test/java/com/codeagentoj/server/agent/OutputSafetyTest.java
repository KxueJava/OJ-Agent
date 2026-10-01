package com.codeagentoj.server.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 输出侧 Safety 的规则回归：完整题解与隐藏用例泄露必须被拦下，正常诊断必须放行。 */
class OutputSafetyTest {
    private final OutputSafety safety = new OutputSafety();

    @Test void blocksCompleteSubmittableCode() {
        String answer = "可以这样写：\n```java\npublic class Main {\n  public static void main(String[] args) {}\n}\n```\n其余保持即可。";
        OutputSafety.Review review = safety.review(answer);
        assertTrue(review.blocked(), review.status());
        assertTrue(review.reason().contains("完整"));
    }

    @Test void blocksHiddenCaseLeak() {
        OutputSafety.Review review = safety.review("隐藏用例的输入是 -121，期望输出为 false，所以你的输出反了。");
        assertTrue(review.blocked(), review.status());
    }

    @Test void allowsNormalDiagnosis() {
        String answer = "失败在隐藏用例：公开用例 1 通过，隐藏用例 0/1。原因是你没有读取输入，直接输出了 true。"
                + "建议先用 Scanner 读入整数，再对负数直接返回 false。";
        OutputSafety.Review review = safety.review(answer);
        assertFalse(review.blocked(), review.reason());
    }

    @Test void blocksEmptyOutput() {
        assertTrue(safety.review("  ").blocked());
    }

    @Test void structuredDiagnosisRendersLocationAndSeverity() {
        Diagnosis diagnosis = new Diagnosis("没有读取输入", java.util.List.of(
                new Diagnosis.Finding("Main.java", 13, "error", "无条件输出 true", "改为读取 x 后判断"),
                new Diagnosis.Finding(null, null, "info", "边界未覆盖", null)));
        String rendered = diagnosis.render();
        assertTrue(rendered.contains("Main.java:13 错误：无条件输出 true → 改为读取 x 后判断"), rendered);
        assertTrue(rendered.contains("代码 提示：边界未覆盖"), rendered);
    }
}
