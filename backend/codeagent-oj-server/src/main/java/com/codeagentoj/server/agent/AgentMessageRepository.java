package com.codeagentoj.server.agent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 复用 agent_messages 表作为记忆存储：审计与记忆同源，模型当时看到的内容可以逐条回放。
 *
 * <p>表内写入只发生在这里（Controller 不再直接 INSERT agent_messages）：
 * <ul>
 *   <li>参与记忆的轮次走 {@link #saveAll}，in_memory=1，按 seq 单调排序；</li>
 *   <li>只审计的轮次（Safety 拦截、模型不可用时的规则兜底）走 {@link #recordAuditOnly}，in_memory=0。</li>
 * </ul>
 */
@Repository
public class AgentMessageRepository implements ChatMemoryRepository {
    private final JdbcTemplate jdbc;
    private final int maxChars;

    public AgentMessageRepository(JdbcTemplate jdbc, @Value("${app.agent.memory.max-chars:12000}") int maxChars) {
        this.jdbc = jdbc;
        this.maxChars = maxChars;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Long session = sessionId(conversationId);
        if (session == null) return List.of();
        List<Message> loaded = jdbc.query(
                "SELECT role,content FROM agent_messages WHERE session_id=? AND in_memory=1 ORDER BY seq ASC",
                (rs, row) -> toMessage(rs.getString("role"), rs.getString("content")), session);
        return trim(loaded, maxChars);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Long session = sessionId(conversationId);
        if (session == null || messages == null) return;
        for (Message message : messages) {
            boolean user = message instanceof UserMessage;
            insert(session, user ? "USER" : "ASSISTANT", user ? "Supervisor" : "Finalizer", text(message), "PASSED", 1);
        }
    }

    /** 只审计、不进记忆：被 Safety 拦截的轮次，或模型不可用时的规则兜底文案。 */
    public void recordAuditOnly(long sessionId, String safetyStatus, String userContent, String assistantContent) {
        insert(sessionId, "USER", "Supervisor", userContent, safetyStatus, 0);
        insert(sessionId, "ASSISTANT", "Finalizer", assistantContent, safetyStatus, 0);
    }

    @Override
    public List<String> findConversationIds() {
        return jdbc.queryForList("SELECT DISTINCT session_id FROM agent_messages", Long.class).stream().map(String::valueOf).toList();
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Long session = sessionId(conversationId);
        if (session != null) jdbc.update("DELETE FROM agent_messages WHERE session_id=?", session);
    }

    /**
     * 从最新往旧累加字符预算，超出即停止（至少保留最后一条，避免单条超长时取不到任何历史）；
     * 随后丢弃开头的非 user 消息，保证交给模型的历史以 user 轮开始、assistant 轮结束。
     */
    static List<Message> trim(List<Message> messages, int maxChars) {
        if (messages == null || messages.isEmpty()) return List.of();
        int budget = Math.max(1, maxChars);
        int start = messages.size();
        int used = 0;
        for (int index = messages.size() - 1; index >= 0; index--) {
            int length = text(messages.get(index)).length();
            if (used + length > budget && start != messages.size()) break;
            used += length;
            start = index;
        }
        while (start < messages.size() && !(messages.get(start) instanceof UserMessage)) start++;
        return start >= messages.size() ? List.of() : List.copyOf(messages.subList(start, messages.size()));
    }

    private void insert(long sessionId, String role, String agent, String content, String safetyStatus, int inMemory) {
        jdbc.update("INSERT INTO agent_messages (id,session_id,role,agent,content,safety_status,in_memory) VALUES (?,?,?,?,?,?,?)",
                id(), sessionId, role, agent, content, safetyStatus, inMemory);
    }

    private static Message toMessage(String role, String content) {
        return "USER".equals(role) ? new UserMessage(content) : new AssistantMessage(content);
    }

    private static String text(Message message) {
        String value = message.getText();
        return value == null ? "" : value;
    }

    private static Long sessionId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        try { return Long.parseLong(conversationId.trim()); } catch (NumberFormatException ignored) { return null; }
    }

    private static long id() { return Math.abs(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)); }
}
