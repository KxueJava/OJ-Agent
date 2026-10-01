package com.codeagentoj.server.agent.tools;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 提交维度的只读工具。
 *
 * <p>安全约束（对齐 project-plan §2「判题结果由 OJ 决定」）：
 * <ul>
 *   <li>只读当前用户自己的提交（SQL 里始终带 {@code user_id=?}）；</li>
 *   <li>公开用例返回逐条结果；隐藏用例<b>只返回通过计数</b>，永不返回其输入或期望输出。</li>
 * </ul>
 */
@Component
public class SubmissionTools {
    private final JdbcTemplate jdbc;
    private final ToolAudit audit;

    public SubmissionTools(JdbcTemplate jdbc, ToolAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Tool(description = "列出当前用户在这道题上的历史提交（提交 ID、语言、状态、耗时、时间）。只能看到该用户自己的记录。")
    public String getSubmissionHistory(@ToolParam(description = "返回条数，默认 5，最多 10", required = false) Integer limit, ToolContext context) {
        int size = limit == null ? 5 : Math.max(1, Math.min(10, limit));
        return audit.run(context, "getSubmissionHistory", "limit=" + size, tenant -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,language,status,verdict_message,runtime_ms,created_at FROM submissions WHERE user_id=? AND problem_version_id=? ORDER BY created_at DESC LIMIT ?",
                    ToolAudit.userId(tenant), ToolAudit.problemVersion(tenant), size);
            if (rows.isEmpty()) return "当前用户在这道题上还没有提交记录。";
            StringBuilder text = new StringBuilder("最近 " + rows.size() + " 次提交：\n");
            for (Map<String, Object> row : rows) {
                text.append("- #").append(row.get("id")).append(' ').append(row.get("status"))
                        .append("，语言=").append(row.get("language"))
                        .append("，耗时=").append(row.get("runtime_ms") == null ? "-" : row.get("runtime_ms") + "ms")
                        .append("，时间=").append(row.get("created_at"))
                        .append("，说明=").append(row.get("verdict_message") == null ? "-" : row.get("verdict_message"))
                        .append('\n');
            }
            return text.toString();
        });
    }

    @Tool(description = "读取当前用户某次提交的判题结果：状态、语言、耗时、内存、公开用例逐条结果，以及隐藏用例的通过计数（不包含隐藏用例的输入与期望输出）。")
    public String getSubmissionDetail(@ToolParam(description = "提交 ID，先用 getSubmissionHistory 拿到") Long submissionId, ToolContext context) {
        return audit.run(context, "getSubmissionDetail", "submissionId=" + submissionId, tenant -> {
            if (submissionId == null) return "需要提供提交 ID。";
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,language,status,verdict_message,runtime_ms,memory_kb FROM submissions WHERE id=? AND user_id=?",
                    submissionId, ToolAudit.userId(tenant));
            if (rows.isEmpty()) return "没有这条提交记录，或它不属于当前用户。";
            Map<String, Object> row = rows.get(0);
            StringBuilder text = new StringBuilder();
            text.append("提交 #").append(row.get("id")).append("：").append(row.get("status"))
                    .append("，语言=").append(row.get("language"))
                    .append("，耗时=").append(row.get("runtime_ms") == null ? "-" : row.get("runtime_ms") + "ms")
                    .append("，内存=").append(row.get("memory_kb") == null ? "-" : row.get("memory_kb") + "KB")
                    .append("\n判题说明：").append(row.get("verdict_message") == null ? "-" : row.get("verdict_message"))
                    .append('\n');

            List<Map<String, Object>> publicCases = jdbc.queryForList(
                    "SELECT sc.verdict,sc.runtime_ms,sc.output_summary FROM submission_cases sc JOIN test_cases tc ON tc.id=sc.test_case_id WHERE sc.submission_id=? AND tc.visibility='PUBLIC' ORDER BY sc.id",
                    submissionId);
            if (publicCases.isEmpty()) {
                text.append("这次提交没有公开用例结果。\n");
            } else {
                int index = 1;
                for (Map<String, Object> item : publicCases) {
                    text.append("公开用例 ").append(index++).append("：").append(item.get("verdict"));
                    if (item.get("output_summary") != null) text.append("（输出摘要：").append(item.get("output_summary")).append('）');
                    text.append('\n');
                }
            }

            Map<String, Object> hidden = jdbc.queryForMap(
                    "SELECT COUNT(*) AS total, SUM(CASE WHEN sc.verdict='AC' THEN 1 ELSE 0 END) AS passed FROM submission_cases sc JOIN test_cases tc ON tc.id=sc.test_case_id WHERE sc.submission_id=? AND tc.visibility='HIDDEN'",
                    submissionId);
            long total = hidden.get("total") instanceof Number number ? number.longValue() : 0L;
            long passed = hidden.get("passed") instanceof Number number ? number.longValue() : 0L;
            if (total > 0) {
                text.append("隐藏用例：通过 ").append(passed).append('/').append(total).append("（按规则不展示其输入与期望输出）\n");
            }
            return text.toString();
        });
    }

    @Tool(description = "读取当前用户最近一次“运行样例”的结果（公开样例逐条状态与输出摘要）。")
    public String getLatestPublicRun(ToolContext context) {
        return audit.run(context, "getLatestPublicRun", "", tenant -> {
            List<Map<String, Object>> runs = jdbc.queryForList(
                    "SELECT id,status,verdict_message,runtime_ms,created_at FROM public_runs WHERE user_id=? AND problem_version_id=? ORDER BY created_at DESC LIMIT 1",
                    ToolAudit.userId(tenant), ToolAudit.problemVersion(tenant));
            if (runs.isEmpty()) return "当前用户在这道题上还没有公开样例运行记录。";
            Map<String, Object> run = runs.get(0);
            StringBuilder text = new StringBuilder("最近一次运行 #" + run.get("id") + "：" + run.get("status")
                    + "，耗时=" + (run.get("runtime_ms") == null ? "-" : run.get("runtime_ms") + "ms")
                    + "，说明=" + (run.get("verdict_message") == null ? "-" : run.get("verdict_message")) + '\n');
            List<Map<String, Object>> cases = jdbc.queryForList(
                    "SELECT verdict,runtime_ms,output_summary FROM public_run_cases WHERE run_id=? ORDER BY id", run.get("id"));
            int index = 1;
            for (Map<String, Object> item : cases) {
                text.append("样例 ").append(index++).append("：").append(item.get("verdict"));
                if (item.get("output_summary") != null) text.append("，输出摘要：").append(item.get("output_summary"));
                text.append('\n');
            }
            return text.toString();
        });
    }
}
