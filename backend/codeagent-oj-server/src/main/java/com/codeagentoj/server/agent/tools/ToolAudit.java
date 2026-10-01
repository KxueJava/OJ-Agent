package com.codeagentoj.server.agent.tools;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 所有工具的公共入口：租户校验 → 执行 → 截断 → 审计。
 *
 * <p>约定：
 * <ul>
 *   <li>租户信息只从 {@link ToolContext} 读取，不从工具参数读取；</li>
 *   <li>返回值统一截断到 {@value #MAX_CHARS} 字符，避免把整个源码/日志灌进上下文；</li>
 *   <li>异常不外抛，转成可读文本返回给模型，让它可以换一种方式继续；</li>
 *   <li>每次调用写一行 {@code agent_tool_calls}，审计失败不影响工具结果。</li>
 * </ul>
 */
@Component
public class ToolAudit {
    static final int MAX_CHARS = 2000;
    private final JdbcTemplate jdbc;

    public ToolAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String run(ToolContext context, String tool, String args, Function<Map<String, Object>, String> body) {
        Map<String, Object> tenant = tenant(context);
        long started = System.nanoTime();
        boolean ok = true;
        String result;
        try {
            if (userId(tenant) == null) {
                ok = false;
                result = "缺少登录上下文，无法读取数据。";
            } else {
                result = truncate(body.apply(tenant));
            }
        } catch (RuntimeException error) {
            ok = false;
            result = "工具执行失败：" + error.getClass().getSimpleName();
        }
        record(tenant, tool, args, result, ok, (System.nanoTime() - started) / 1_000_000L);
        return result;
    }

    static String truncate(String value) {
        if (value == null) return "";
        if (value.length() <= MAX_CHARS) return value;
        return value.substring(0, MAX_CHARS) + "\n…（结果已截断，原始长度 " + value.length() + " 字符）";
    }

    private void record(Map<String, Object> tenant, String tool, String args, String result, boolean ok, long latencyMs) {
        try {
            jdbc.update("INSERT INTO agent_tool_calls (id,session_id,tool,args_json,result_chars,ok,latency_ms) VALUES (?,?,?,?,?,?,?)",
                    id(), tenant.get(ToolContextKeys.SESSION_ID), tool, args, result == null ? 0 : result.length(), ok ? 1 : 0, (int) latencyMs);
        } catch (RuntimeException ignored) {
            // 审计写入失败不能影响工具本身的返回
        }
    }

    static Map<String, Object> tenant(ToolContext context) {
        Map<String, Object> values = context == null ? null : context.getContext();
        return values == null ? Map.of() : values;
    }

    static Long userId(Map<String, Object> tenant) {
        Object value = tenant.get(ToolContextKeys.USER_ID);
        return value instanceof Number number ? number.longValue() : null;
    }

    static long problemVersion(Map<String, Object> tenant) {
        Object value = tenant.get(ToolContextKeys.PROBLEM_VERSION);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static long id() {
        return Math.abs(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    }
}
