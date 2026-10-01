package com.codeagentoj.server.agent.tools;

/**
 * 工具租户上下文的键。
 *
 * <p>这些值由服务端从 JWT 与请求体派生后放进 {@code ChatClient.prompt().toolContext(...)}，
 * 只以 {@code ToolContext} 参数形式注入工具方法，<b>绝不作为模型可见的工具参数</b>，
 * 因此模型无法通过构造参数来读取其他用户或其他题目的数据。
 */
public final class ToolContextKeys {
    public static final String USER_ID = "userId";
    public static final String PROBLEM_VERSION = "problemVersion";
    public static final String SESSION_ID = "sessionId";

    private ToolContextKeys() {}
}
