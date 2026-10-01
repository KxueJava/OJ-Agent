package com.codeagentoj.server.agent;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Spring AI adapter for DeepSeek's OpenAI-compatible chat API. */
@Service
public class DeepSeekClient {
    /** 与 application.yml 里 spring.ai.openai.api-key 的占位默认值保持一致。 */
    static final String PLACEHOLDER_KEY = "disabled";
    private final ChatClient chatClient;
    private final String apiKey;

    public DeepSeekClient(ChatClient.Builder builder,
                          @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.chatClient = builder.build();
        this.apiKey = apiKey;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank() && !PLACEHOLDER_KEY.equals(apiKey.trim());
    }

    /** 无记忆的单轮调用（保留旧签名，作为记忆链路的回滚开关）。 */
    public String answer(String systemPrompt, String userPrompt) {
        return answer(systemPrompt, List.of(), userPrompt);
    }

    /**
     * 记忆走真正的 Message 序列注入，而不是把历史拼进 user 文本：
     * 历史由 AgentController 通过 ChatMemory 显式读写，这里只负责"把消息发出去"。
     */
    public String answer(String systemPrompt, List<Message> history, String userPrompt) {
        return answer(systemPrompt, history, userPrompt, null, null);
    }

    /**
     * 带工具的一轮调用。{@code tools} 是暴露给模型的工具对象，{@code toolContext} 是服务端派生的只读租户上下文
     * （模型看不到其中内容，只能由工具方法以 ToolContext 形式接收），因此模型无法通过工具参数越权取数。
     */
    public String answer(String systemPrompt, List<Message> history, String userPrompt, Object[] tools, Map<String, Object> toolContext) {
        if (!configured()) throw new IllegalStateException("模型 API Key 未配置（DEEPSEEK_API_KEY / spring.ai.openai.api-key）");
        var prompt = chatClient.prompt().system(systemPrompt);
        if (history != null && !history.isEmpty()) prompt = prompt.messages(history.toArray(Message[]::new));
        if (tools != null && tools.length > 0) prompt = prompt.tools(tools);
        if (toolContext != null && !toolContext.isEmpty()) prompt = prompt.toolContext(toolContext);
        String answer = prompt.user(userPrompt).call().content();
        if (answer == null || answer.isBlank()) throw new IllegalStateException("DeepSeek 返回为空");
        return answer.trim();
    }

    /** 结构化输出：Spring AI 会附带格式指令并把模型产物反序列化为对象。 */
    public <T> T entity(String systemPrompt, String userPrompt, Object[] tools, Map<String, Object> toolContext, Class<T> type) {
        if (!configured()) throw new IllegalStateException("模型 API Key 未配置（DEEPSEEK_API_KEY / spring.ai.openai.api-key）");
        var prompt = chatClient.prompt().system(systemPrompt);
        if (tools != null && tools.length > 0) prompt = prompt.tools(tools);
        if (toolContext != null && !toolContext.isEmpty()) prompt = prompt.toolContext(toolContext);
        T value = prompt.user(userPrompt).call().entity(type);
        if (value == null) throw new IllegalStateException("模型未返回结构化结果");
        return value;
    }

    /** 真流式：按 token 返回，调用方负责消费 {@code Flux}、落库与推送给前端。 */
    public reactor.core.publisher.Flux<String> stream(String systemPrompt, List<Message> history, String userPrompt, Object[] tools, Map<String, Object> toolContext) {
        if (!configured()) throw new IllegalStateException("模型 API Key 未配置（DEEPSEEK_API_KEY / spring.ai.openai.api-key）");
        var prompt = chatClient.prompt().system(systemPrompt);
        if (history != null && !history.isEmpty()) prompt = prompt.messages(history.toArray(Message[]::new));
        if (tools != null && tools.length > 0) prompt = prompt.tools(tools);
        if (toolContext != null && !toolContext.isEmpty()) prompt = prompt.toolContext(toolContext);
        return prompt.user(userPrompt).stream().content();
    }
}
