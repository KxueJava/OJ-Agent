package com.codeagentoj.server.agent;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆窗口策略：只按条数淘汰（默认 20 条 ≈ 10 轮），
 * 字符/token 预算由 {@link AgentMessageRepository#trim} 在读取时兜底。
 */
@Configuration
public class AgentMemoryConfig {
    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, @Value("${app.agent.memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }
}
