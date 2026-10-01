package com.codeagentoj.server.agent.tools;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 学习维度的只读工具：只看当前用户自己的错题与今日计划。 */
@Component
public class LearningTools {
    private final JdbcTemplate jdbc;
    private final ToolAudit audit;

    public LearningTools(JdbcTemplate jdbc, ToolAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Tool(description = "读取当前用户的学习上下文：待复习错题（最多 3 条）与今日训练计划完成度。只有该用户自己的数据。")
    public String getLearningContext(ToolContext context) {
        return audit.run(context, "getLearningContext", "", tenant -> {
            long user = ToolAudit.userId(tenant);
            int openMistakes = count("SELECT COUNT(*) FROM mistake_books WHERE user_id=? AND status='OPEN'", user);
            int events = count("SELECT COUNT(*) FROM learning_events WHERE user_id=?", user);
            List<Map<String, Object>> mistakes = jdbc.queryForList(
                    "SELECT p.title,m.error_type,m.diagnosis FROM mistake_books m JOIN problems p ON p.id=m.problem_id WHERE m.user_id=? AND m.status='OPEN' ORDER BY m.created_at DESC LIMIT 3",
                    user);
            List<Map<String, Object>> plans = jdbc.queryForList(
                    "SELECT target_count,completed_count FROM study_plans WHERE user_id=? AND plan_date=CURRENT_DATE", user);

            StringBuilder text = new StringBuilder();
            text.append("学习事件累计 ").append(events).append(" 条，待复习错题 ").append(openMistakes).append(" 道\n");
            if (plans.isEmpty()) {
                text.append("今日计划：尚未生成\n");
            } else {
                Map<String, Object> plan = plans.get(0);
                text.append("今日计划：已完成 ").append(plan.get("completed_count")).append('/').append(plan.get("target_count")).append('\n');
            }
            if (mistakes.isEmpty()) {
                text.append("没有待复习的错题。\n");
            } else {
                text.append("待复习错题：\n");
                for (Map<String, Object> item : mistakes) {
                    text.append("- ").append(item.get("title")).append("（").append(item.get("error_type")).append("）：")
                            .append(item.get("diagnosis")).append('\n');
                }
            }
            return text.toString();
        });
    }

    private int count(String sql, long user) {
        Integer value = jdbc.queryForObject(sql, Integer.class, user);
        return value == null ? 0 : value;
    }
}
