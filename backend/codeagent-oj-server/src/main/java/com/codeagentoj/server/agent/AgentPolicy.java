package com.codeagentoj.server.agent;

import java.util.regex.Pattern;

/** Pure routing and safety rules kept separate so they can be regression-tested. */
public final class AgentPolicy {
    private static final Pattern INJECTION = Pattern.compile("(?i)(ignore\\s+(all|previous)|system\\s+prompt|reveal\\s+(the\\s+)?answer|隐藏测试|标准答案|数据库|shell|命令行)");
    private static final Pattern REVIEW = Pattern.compile("(?i)(复杂度|边界|代码质量|review|审查|可读性)");
    private static final Pattern LEARN = Pattern.compile("(?i)(推荐|错题|复习|学习计划|下一题)");
    private static final Pattern DEBUG = Pattern.compile("(?i)(编译|报错|异常|错误|WA|RE|TLE|调试)");

    private AgentPolicy() {}

    public static boolean blocked(String message) { return message != null && INJECTION.matcher(message).find(); }

    public static String intent(String message) {
        String value = message == null ? "" : message;
        if (REVIEW.matcher(value).find()) return "REVIEW";
        if (LEARN.matcher(value).find()) return "LEARN";
        return DEBUG.matcher(value).find() ? "DEBUG" : "TUTOR";
    }
}
