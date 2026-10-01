package com.codeagentoj.server.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 工具层的安全回归测试：不连数据库，只记录发出的 SQL 与参数，
 * 断言"隐藏用例的输入/期望输出永远不会出现在查询里""查询始终限定当前用户"。
 */
class SubmissionToolsTest {
    private static final Map<String, Object> TENANT = Map.of(
            ToolContextKeys.USER_ID, 7L, ToolContextKeys.PROBLEM_VERSION, 2101L, ToolContextKeys.SESSION_ID, 3L);

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) values.put((String) pairs[index], pairs[index + 1]);
        return values;
    }

    private static boolean matches(String sql, String... needles) {
        String lower = sql.toLowerCase();
        for (String needle : needles) if (!lower.contains(needle)) return false;
        return true;
    }

    /** 只记录 SQL/参数并按查询形状返回预制结果的 JdbcTemplate。 */
    static class RecordingJdbc extends JdbcTemplate {
        final List<String> statements = new ArrayList<>();
        final List<Object[]> arguments = new ArrayList<>();

        @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
            statements.add(sql); arguments.add(args);
            if (matches(sql, "from submissions where id=?")) {
                return List.of(row("id", 99L, "language", "JAVA_21", "status", "WA",
                        "verdict_message", "hidden case failed", "runtime_ms", 12, "memory_kb", 3456));
            }
            if (matches(sql, "from submission_cases", "visibility='public'")) {
                return List.of(row("verdict", "AC", "runtime_ms", 12, "output_summary", "true"));
            }
            if (matches(sql, "from submissions where user_id=?")) {
                return List.of(row("id", 99L, "language", "JAVA_21", "status", "WA",
                        "verdict_message", "hidden case failed", "runtime_ms", 12, "created_at", "2026-10-01 13:00:00"));
            }
            return List.of();
        }

        @Override public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            statements.add(sql); arguments.add(args); return List.of();
        }

        @Override public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            statements.add(sql); arguments.add(args); return null;
        }

        @Override public Map<String, Object> queryForMap(String sql, Object... args) {
            statements.add(sql); arguments.add(args);
            return row("total", 2L, "passed", 1L);
        }

        @Override public int update(String sql, Object... args) {
            statements.add(sql); arguments.add(args); return 0;
        }
    }

    private static ToolContext context(Map<String, Object> tenant) {
        return new ToolContext(tenant);
    }

    @Test void submissionDetailNeverSelectsHiddenInputsOrExpectedOutputs() {
        RecordingJdbc jdbc = new RecordingJdbc();
        SubmissionTools tools = new SubmissionTools(jdbc, new ToolAudit(jdbc));

        String result = tools.getSubmissionDetail(99L, context(TENANT));

        assertTrue(result.contains("WA"), result);
        for (String sql : jdbc.statements) {
            String normalized = sql.toLowerCase();
            assertFalse(normalized.contains("expected_output"), "不得查询期望输出：" + sql);
            assertFalse(normalized.contains("tc.input_text"), "不得查询用例输入：" + sql);
        }
        assertTrue(jdbc.statements.stream().anyMatch(sql -> sql.contains("tc.visibility='PUBLIC'")), "公开用例查询必须显式过滤 visibility");
        assertTrue(jdbc.statements.stream().anyMatch(sql -> sql.contains("tc.visibility='HIDDEN'") && sql.contains("COUNT(")),
                "隐藏用例只能以计数形式出现");
    }

    @Test void everySubmissionQueryIsScopedToTheCurrentUser() {
        RecordingJdbc jdbc = new RecordingJdbc();
        SubmissionTools tools = new SubmissionTools(jdbc, new ToolAudit(jdbc));

        tools.getSubmissionHistory(3, context(TENANT));
        tools.getSubmissionDetail(99L, context(TENANT));
        tools.getLatestPublicRun(context(TENANT));

        for (int index = 0; index < jdbc.statements.size(); index++) {
            String sql = jdbc.statements.get(index);
            String lower = sql.toLowerCase();
            if (!lower.startsWith("select")) continue;
            boolean userScoped = lower.contains("user_id=?");
            boolean childScoped = lower.contains("submission_id=?") || lower.contains("run_id=?");
            assertTrue(userScoped || childScoped, "查询必须以用户、或该用户的提交/运行为边界：" + sql);
            if (userScoped) assertTrue(hasArg(jdbc, index, 7L), "按用户过滤的查询必须带当前用户 ID：" + sql);
        }
        assertTrue(jdbc.statements.stream().anyMatch(sql -> sql.toLowerCase().contains("from submissions where id=? and user_id=?")),
                "按提交 ID 取详情时必须同时校验归属");
    }

    private static boolean hasArg(RecordingJdbc jdbc, int index, long value) {
        for (Object argument : jdbc.arguments.get(index)) {
            if (argument instanceof Number number && number.longValue() == value) return true;
        }
        return false;
    }

    @Test void missingTenantNeverReadsData() {
        RecordingJdbc jdbc = new RecordingJdbc();
        SubmissionTools tools = new SubmissionTools(jdbc, new ToolAudit(jdbc));

        String result = tools.getSubmissionHistory(5, context(Map.of()));

        assertTrue(result.contains("缺少登录上下文"), result);
        assertTrue(jdbc.statements.stream().noneMatch(sql -> sql.toLowerCase().startsWith("select")), "无租户时不得发起查询");
    }

    @Test void longResultsAreTruncated() {
        String truncated = ToolAudit.truncate("x".repeat(ToolAudit.MAX_CHARS + 500));
        assertTrue(truncated.startsWith("x".repeat(ToolAudit.MAX_CHARS)));
        assertTrue(truncated.contains("已截断"));
        assertEquals("short", ToolAudit.truncate("short"));
        assertEquals("", ToolAudit.truncate(null));
    }
}
