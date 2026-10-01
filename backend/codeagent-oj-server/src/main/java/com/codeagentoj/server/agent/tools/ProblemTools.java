package com.codeagentoj.server.agent.tools;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 题目维度的只读工具：只暴露公开信息（题面、约束、标签、公开样例）。 */
@Component
public class ProblemTools {
    private final JdbcTemplate jdbc;
    private final ToolAudit audit;

    public ProblemTools(JdbcTemplate jdbc, ToolAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Tool(description = "读取当前题目的公开信息：标题、难度、标签、题面与约束。不包含隐藏测试、标准答案或判题数据。")
    public String getProblemBrief(ToolContext context) {
        return audit.run(context, "getProblemBrief", "", tenant -> {
            long version = ToolAudit.problemVersion(tenant);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT p.slug,p.title,p.difficulty,pv.statement_md,pv.constraints_md FROM problem_versions pv JOIN problems p ON p.id=pv.problem_id WHERE pv.id=?",
                    version);
            if (rows.isEmpty()) return "找不到当前题目。";
            Map<String, Object> row = rows.get(0);
            List<String> tags = jdbc.queryForList(
                    "SELECT t.name FROM problem_tags pt JOIN tags t ON t.id=pt.tag_id WHERE pt.problem_id=(SELECT problem_id FROM problem_versions WHERE id=?) ORDER BY t.name",
                    String.class, version);
            return "题目：" + row.get("title") + "（" + row.get("slug") + "，难度 " + row.get("difficulty") + "）"
                    + "\n标签：" + (tags.isEmpty() ? "无" : String.join("、", tags))
                    + "\n题面：\n" + row.get("statement_md")
                    + "\n约束：\n" + row.get("constraints_md");
        });
    }

    @Tool(description = "列出当前题目的公开样例（输入与期望输出）。这些样例用户也能在题面上看到。")
    public String listExamples(ToolContext context) {
        return audit.run(context, "listExamples", "", tenant -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT display_order,input_text,output_text FROM examples WHERE problem_version_id=? ORDER BY display_order",
                    ToolAudit.problemVersion(tenant));
            if (rows.isEmpty()) return "这道题没有公开样例。";
            StringBuilder text = new StringBuilder();
            for (Map<String, Object> row : rows) {
                text.append("样例 ").append(row.get("display_order")).append("：\n输入：").append(row.get("input_text"))
                        .append("\n输出：").append(row.get("output_text")).append('\n');
            }
            return text.toString();
        });
    }
}
