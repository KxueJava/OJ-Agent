package com.codeagentoj.server.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/** 只测纯函数 trim：窗口裁剪、预算截断、以及"历史必须以 user 轮开始"的约束。 */
class AgentMessageRepositoryTest {
    private static Message user(String text) { return new UserMessage(text); }
    private static Message agent(String text) { return new AssistantMessage(text); }
    private static List<String> texts(List<Message> messages) { return messages.stream().map(Message::getText).toList(); }

    @Test void keepsNewestMessagesInsideBudget() {
        List<Message> history = List.of(user("aaaa"), agent("bbbb"), user("cccc"), agent("dddd"));
        // 预算 12：从最新往前只能装下 dddd + cccc + bbbb，随后丢弃开头的 assistant 轮
        assertEquals(List.of("cccc", "dddd"), texts(AgentMessageRepository.trim(history, 12)));
    }

    @Test void alwaysKeepsTheLastMessageEvenWhenOversized() {
        List<Message> history = List.of(user("x".repeat(500)));
        assertEquals(List.of("x".repeat(500)), texts(AgentMessageRepository.trim(history, 10)));
    }

    @Test void dropsLeadingAssistantMessagesSoHistoryStartsWithUser() {
        List<Message> history = List.of(agent("a1"), user("q2"), agent("a2"));
        assertEquals(List.of("q2", "a2"), texts(AgentMessageRepository.trim(history, 1000)));
    }

    @Test void returnsEmptyWhenNoUserTurnIsAvailable() {
        assertTrue(AgentMessageRepository.trim(List.of(agent("a1")), 1000).isEmpty());
        assertTrue(AgentMessageRepository.trim(List.of(), 1000).isEmpty());
        assertTrue(AgentMessageRepository.trim(null, 1000).isEmpty());
    }
}
