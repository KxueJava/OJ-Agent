package com.codeagentoj.server.agent;

import java.util.List;

/**
 * 结构化诊断结果。Spring AI 用 Jackson 反序列化，字段名即 JSON 键，
 * 前端据此渲染 `文件:行号`（而不是解析自然语言）。
 */
public record Diagnosis(String summary, List<Finding> findings) {
    public record Finding(String file, Integer line, String severity, String message, String suggestion) {
        String location() {
            String name = file == null || file.isBlank() ? "代码" : file;
            return line == null ? name : name + ":" + line;
        }

        String severityLabel() {
            if (severity == null) return "提示";
            return switch (severity.trim().toLowerCase()) {
                case "error", "high", "critical" -> "错误";
                case "warning", "medium" -> "警告";
                default -> "提示";
            };
        }
    }

    /** 渲染成人类可读文本，保证不依赖结构化字段的下游（卡片、审计、记忆）仍然可用。 */
    public String render() {
        StringBuilder text = new StringBuilder(summary == null ? "" : summary.trim());
        if (findings != null) {
            for (Finding finding : findings) {
                text.append("\n- ").append(finding.location()).append(' ').append(finding.severityLabel())
                        .append('：').append(finding.message() == null ? "" : finding.message());
                if (finding.suggestion() != null && !finding.suggestion().isBlank()) {
                    text.append(" → ").append(finding.suggestion());
                }
            }
        }
        return text.toString().trim();
    }
}
