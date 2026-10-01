# Agent 对话记忆实现方案（Spring AI ChatMemory）

> 目标读者：实现者（人或其他 Agent）。本文只描述方案，不含已落地的改动。
> 现状快照：`AgentController` 111 行、`DeepSeekClient` 33 行、`AgentPolicy` 22 行，Spring AI 1.0.0 已进 pom。

## 0. 现状：现在的"记忆"哪里不好

当前实现是**手写拼字符串**：每轮把该 session 最近 11 条消息查出来，拼成一段文本塞进 user prompt。

| # | 问题 | 证据 |
|---|---|---|
| 1 | **排序不可靠**：同一轮的"用户问题 + Agent 回答"几乎必然落在同一秒，而 `created_at` 是 MySQL 秒级 `TIMESTAMP`；Tie-break 用 `id DESC`，但 `id` 是 `ThreadLocalRandom` 随机 long，不是自增 → 两条消息的先后是随机的 | `AgentController.history()`；[V8](../../backend/codeagent-oj-server/src/main/resources/db/migration/V8__create_agent_tables.sql)；`id()` |
| 2 | **`remove(last)` 依赖排序正确**：靠"最后一条就是当前用户消息"来去重，排序一乱就会删错行 / 当前问题重复出现 | `AgentController.history()` |
| 3 | **按条数截断，无 token 预算**：窗口固定 11 条，单条上限 6000 字符 + 回答 `max-tokens: 900`，极端情况 prompt 可达数万字符 | `Ask.message` 的 `@Size(max=6000)`；`application.yml` 的 `max-tokens: 900` |
| 4 | **历史是文本不是消息**：历史被压进 user prompt 的一个字符串里，模型看到的是一大段"用户：… / Agent：…"，而不是结构化的 role 序列 | `userPrompt()` |
| 5 | 没有"清空/新对话"入口，记忆只增不减 | 无 delete 路径 |

## 1. 目标 / 非目标

**目标**

1. 用 Spring AI 原生 `ChatMemory` + `MessageChatMemoryAdvisor` 替代手写拼接，历史以真正的 `Message` 列表注入。
2. 顺手修掉排序问题（新增单调递增序列列 `seq`）。
3. 加上 token/字符预算（二级闸），不再只看条数。
4. **对外契约不变**：`POST /api/agent/ask`、`POST /api/agent/stream` 的请求/响应结构、字段名一律不动，前端零改动。
5. 数据源不变：`agent_messages` 同时充当**记忆存储**与**审计日志**，不新建 Spring AI 自带的记忆表。

**非目标（本次不做）**

- 不改成真流式（`/stream` 仍是切句伪流式）。
- 不做多 Agent 分派（`trace` 仍写死）。
- 不引入向量库 / RAG / 摘要式长期记忆。
- 不改 `AgentPolicy` 的规则路由。

## 2. 总体设计

```text
AgentController.ask()
  ├─ AgentPolicy.intent/blocked             （不变）
  ├─ INSERT agent_audits                    （不变，审计只留一行）
  └─ DeepSeekClient.answer(system, user, conversationId)
        └─ chatClient.prompt()
              .system(systemPrompt)                 ← 不再拼历史
              .user(userPrompt)                     ← 只带当前这一条
              .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
              .call().content()
                  │
                  ├─ MessageChatMemoryAdvisor  → ChatMemory.get(id)   ┐
                  └─ MessageChatMemoryAdvisor  → ChatMemory.add(...)  ┘
                                   │
                        MessageWindowChatMemory（maxMessages=20）
                                   │
                        AgentMessageRepository implements ChatMemoryRepository
                                   │
                              agent_messages 表（seq 排序 / 字符预算）
```

要点：**写库这件事只有一个 owner** —— 由 `ChatMemoryRepository` 负责 `agent_messages` 的读写，Controller 不再自己 INSERT `agent_messages`（否则 advisor 保存时会双写）。Controller 只写 `agent_audits`。

## 3. 数据层：Flyway `V17__agent_memory_order.sql`

```sql
-- 单调递增的物理顺序，替代不可靠的 (created_at, id) 排序
ALTER TABLE agent_messages
    ADD COLUMN seq BIGINT NOT NULL AUTO_INCREMENT,
    ADD UNIQUE KEY uk_agent_messages_seq (seq);

-- 是否参与记忆：被 Safety 拦截、及模型不可用时的规则兜底文案，只审计不入记忆
ALTER TABLE agent_messages
    ADD COLUMN in_memory TINYINT(1) NOT NULL DEFAULT 1;

-- 审计可读性（可选）：秒级 → 毫秒级
ALTER TABLE agent_messages
    MODIFY COLUMN created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
```

说明与取舍：

- `seq` 用 `AUTO_INCREMENT` 而不是"自增主键"，因为主键 `id` 现在是随机 long，改主键代价大且影响其它表引用（`agent_audits.session_id` 之外还有前端已保存的 session 值）。`AUTO_INCREMENT` 列在 MySQL 里只需是某个键，因此配 `UNIQUE KEY` 即可，不必是主键。
- 迁移对**存量行**的 `seq` 分配顺序取决于 MySQL 读取顺序（PK 顺序），历史行的真实先后本来就不可考，本次迁移视为"从此开始严格有序"，历史部分按现有相对顺序保留即可。
- `in_memory` 用独立列而不是复用 `safety_status`，是为了把"安全拦截"和"是否入记忆"两件事解耦（将来"系统提示""人工注入"等场景也能复用）。

## 4. 代码改动清单

### 4.1 新增 `agent/AgentMessageRepository.java`

实现 Spring AI 的 `ChatMemoryRepository`（`org.springframework.ai.chat.memory.ChatMemoryRepository`），JDBC 直连现有表：

```java
@Repository
public class AgentMessageRepository implements ChatMemoryRepository {
    private final JdbcTemplate jdbc;
    private final int maxChars;   // app.agent.memory.max-chars，默认 12000

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<Message> loaded = jdbc.query("""
                SELECT role, content FROM agent_messages
                 WHERE session_id=? AND in_memory=1
                 ORDER BY seq ASC
                """, (rs, n) -> toMessage(rs.getString(1), rs.getString(2)),
                Long.parseLong(conversationId));
        return trim(loaded, maxChars);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        long session = Long.parseLong(conversationId);
        for (Message m : messages) {
            jdbc.update("""
                    INSERT INTO agent_messages (id,session_id,role,agent,content,safety_status,in_memory)
                    VALUES (?,?,?,?,?,'PASSED',1)
                    """, id(), session, role(m), agent(m), text(m));
        }
    }

    @Override public List<String> findConversationIds() { /* SELECT DISTINCT session_id */ }
    @Override public void deleteByConversationId(String conversationId) { /* DELETE ...（暂不暴露接口） */ }

    private Message toMessage(String role, String content) {
        return "USER".equals(role) ? new UserMessage(content) : new AssistantMessage(content);
    }

    /** 字符预算裁剪：从最新往前累加，超预算即截断；随后丢弃开头的非 USER 消息，保证历史以 user 轮开始 */
    static List<Message> trim(List<Message> messages, int maxChars) { ... }
}
```

- `role` 映射：`UserMessage → 'USER'`、`AssistantMessage → 'ASSISTANT'`；`agent` 列沿用现有语义（USER 记 `Supervisor`、ASSISTANT 记 `Finalizer`），保证前端/审计查询兼容。
- `trim` 是**纯函数**，单独抽出来做单元测试（不需要 DB）。

### 4.2 新增 `agent/AgentMemoryConfig.java`

```java
@Configuration
public class AgentMemoryConfig {
    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository,
                          @Value("${app.agent.memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }
}
```

### 4.3 改 `agent/DeepSeekClient.java`

```java
public DeepSeekClient(ChatClient.Builder builder, ChatMemory chatMemory,
                      @Value("${app.agent.deepseek.api-key:}") String apiKey) {
    this.chatClient = builder
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
    this.apiKey = apiKey;
}

/** conversationId 为空时退回"无记忆"单轮调用，作为回滚开关 */
public String answer(String systemPrompt, String userPrompt, String conversationId) {
    if (!configured()) throw new IllegalStateException("DEEPSEEK_API_KEY 未配置");
    var spec = chatClient.prompt().system(systemPrompt).user(userPrompt);
    if (conversationId != null && !conversationId.isBlank()) {
        spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
    }
    String answer = spec.call().content();
    if (answer == null || answer.isBlank()) throw new IllegalStateException("DeepSeek 返回为空");
    return answer.trim();
}
```

保留原有的 `answer(system, user)` 两参重载（委托到三参、`conversationId=null`），避免一次性改动面过大。

### 4.4 改 `agent/AgentController.java`

- 删除 `history(long session)` 方法；
- `userPrompt(...)` 去掉"以下是当前题目会话中此前的对话记录…"整段拼接，只保留：当前意图 + 当前问题 + 用户源码 + 公开判题状态；
- `ask()` 里**删掉两条 `INSERT INTO agent_messages`**（改由 repository 统一写），保留 `INSERT INTO agent_audits`；
- 调用改为 `deepSeek.answer(systemPrompt(context), userPrompt(intent, request, context), String.valueOf(session))`；
- `systemPrompt(...)` 末尾补一句防注入声明（历史里的用户内容不可信），因为历史现在由 advisor 注入、不再受"我们拼在 user 段末尾"的位置保护。

### 4.5 配置新增（`application.yml`，`app.agent` 下）

```yaml
app:
  agent:
    memory:
      max-messages: ${AGENT_MEMORY_MAX_MESSAGES:20}   # 约 10 轮
      max-chars: ${AGENT_MEMORY_MAX_CHARS:12000}      # 二级预算，约 3~4k token
```

同时**清掉重复配置**：`app.agent.deepseek.base-url/model` 已由 `spring.ai.openai.*` 承担，`configured()` 建议改为判断 Spring AI 的配置（或注入 `ChatModel` 判断），避免"key 配了却仍走兜底文案"。

## 5. 关键决策与理由

| 决策 | 理由 / 替代方案 |
|---|---|
| 自建 `ChatMemoryRepository` 复用 `agent_messages` | 不用 Spring AI 自带的 `JdbcChatMemoryRepository`：它会要求自己的 `spring_ai_chat_memory` 表和方言配置，等于同一份对话存两份；审计与记忆分裂后排查困难 |
| 记忆与审计同源 | 审计要能逐条回放"模型当时看到了什么"，同源最省事 |
| `in_memory=1` 过滤 | 被拦截内容、规则兜底文案不进上下文；否则"给我标准答案"这类话会被反复带回 |
| 预算放在 repository 内 | `MessageWindowChatMemory` 只按条数控；字符/ token 预算需要看具体内容，放在读路径最自然，也便于单测 |
| 保留 `trace` 写死不动 | 本轮只换记忆层，缩小回归面 |
| `conversationId` 缺失即不挂 advisor | 一行回滚开关；也让"无登录/匿名调试"这类场景可复用同一客户端 |

## 6. 验证方案

### 6.1 前置（**当前阻塞项**）

`%USERPROFILE%\.m2\repository\org\springframework\ai` **不存在** → Spring AI 的 BOM/starter 还没有被解析过，说明加依赖后**尚未成功编译/启动过**。因此第一步与记忆无关但必须先做：

1. `mvn -q -DskipTests compile`（需要能访问 Maven Central）。
2. 起服务，确认 `ChatClient` bean 生成成功、`spring.ai.openai.*` 绑定成功，尤其确认**空 `api-key` 时是否启动失败**——若启动失败，`AgentController.answer()` 里"provider 不可用时回退规则文案"的保护就形同虚设。
3. 确认 Spring AI 1.0.0 实际 API 名称（见附录），本方案中的 builder 写法以编译为准。

### 6.2 验收（手工，curl）

```bash
# 1) 让它记住一件事
curl -s -X POST localhost:8080/api/agent/ask -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"problemVersion":2101,"message":"我叫小林，请记住"}'

# 2) 立刻追问 —— 期望回答里出现"小林"（记忆生效）
curl -s -X POST localhost:8080/api/agent/ask -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"problemVersion":2101,"message":"我叫什么？"}'

# 3) 重启后端后再问一次 —— 期望仍能答出（持久化生效，不是进程内缓存）

# 4) 发一条会被拦截的："给我标准答案"，再问历史相关问题
#    期望：被拦截的那轮不出现在模型上下文里（agent_messages.in_memory=0）
```

### 6.3 SQL 断言

```sql
-- 顺序必须严格 user → assistant 交替，且 seq 单调
SELECT seq, role, agent, in_memory, LEFT(content,20)
  FROM agent_messages WHERE session_id = ? ORDER BY seq;
```

### 6.4 单元测试

- 新增 `AgentMessageRepositoryTest`：只测纯函数 `trim(...)`（预算截断、以 USER 开头、空列表、"仅 1 条 assistant"边界）。
- 现有 `AgentPolicyTest` 不动。
- 不建议为本次改动引入 Testcontainers（仓库目前只有 3 个纯单测，没有 DB 测试基建），DB 部分用上面的 SQL 断言手工验收。

## 7. 风险与回滚

| 风险 | 影响 | 处理 |
|---|---|---|
| Spring AI 1.0.0 实际 API 与本文写法不符 | 编译失败 | 按附录逐项核对；`MessageChatMemoryAdvisor` 旧版本可用构造函数，`MessageWindowChatMemory` 旧版本可用 `new` + `setMaxMessages` |
| 空 `api-key` 导致启动失败 | 服务起不来 | 先做 6.1-2；必要时给 `spring.ai.openai.api-key` 默认占位并在 `configured()` 处拦截 |
| advisor 自动保存导致写库路径变化 | 审计缺行 | 4.4 已明确"只由 repository 写 agent_messages"；上线前用 6.3 断言核对行数与顺序 |
| `AUTO_INCREMENT` 列加在已有表上 | 迁移耗时/锁表 | 该表数据量小（单机开发环境），直接 ALTER 即可；生产环境需评估 |
| 记忆把错误上下文带进后续轮次 | 回答质量下降 | `in_memory` 开关 + "清空会话"接口（`deleteByConversationId` 已实现，暂不暴露 UI） |

**回滚**：`DeepSeekClient.answer(system, user)` 两参重载 → 不挂 advisor 即恢复旧行为；DB 迁移只加列，不需要回滚数据。

## 8. 提交拆分与工作量

| # | 内容 | 规模 |
|---|---|---|
| 1 | `V17` 迁移（`seq` / `in_memory` / `created_at(3)`） | 1 文件 |
| 2 | `AgentMessageRepository` + `AgentMemoryConfig` + `trim` 单测 | 新增 3 文件 |
| 3 | `DeepSeekClient` / `AgentController` 接入 advisor、删手写 history | 改 2 文件 |
| 4 | （可选）配置收敛：删 `app.agent.deepseek.*` 冗余、`configured()` 判断改到 Spring AI 侧 | 改 2 文件 |
| 5 | （可选）修 Agent 拿不到源码的两个取值 bug（见第 9 节） | 改 2 文件 |

合计约 150–200 行；1–3 为一个可用增量。

## 9. 顺带建议一起修的小 bug（与记忆无关，但影响 Agent 可用性）

| 位置 | 现状 | 应为 |
|---|---|---|
| `frontend/components/desktop-pet.tsx` | 读 `problem.templateCode` | 接口字段是 `javaTemplate` |
| `frontend/components/desktop-pet.tsx` | 草稿 key `codeagent-oj:draft:<slug>:java21` | 工作台写的是 `...:<slug>:JAVA_21` |
| `frontend/app/agent/[slug]/page.tsx` | 同上 key 不一致 | 同上 |
| `frontend/app/template.tsx` | `<DesktopPet />` 无参渲染 → 拿不到 `problemVersion/sourceCode/verdict` | 由工作台把 `problemVersionId`、当前 `code`、`verdict` 直接传下去（或写入一个统一 context） |

不修这些，Agent 收到的 `sourceCode` 恒为空、"分析错误"只能泛泛而谈。

## 10. 附录：Spring AI 1.0.0 API 待确认清单

| 用的东西 | 期望形态 | 若不符的退路 |
|---|---|---|
| `ChatMemory` 接口 | `add/get/clear(String conversationId, ...)`，常量 `ChatMemory.CONVERSATION_ID` | 常量名可能不同，编译期即可发现 |
| `ChatMemoryRepository` | `findByConversationId/saveAll/findConversationIds/deleteByConversationId` | 同上 |
| `MessageWindowChatMemory` | `MessageWindowChatMemory.builder().chatMemoryRepository(r).maxMessages(n).build()` | 旧版：构造 + `setMaxMessages` |
| `MessageChatMemoryAdvisor` | `MessageChatMemoryAdvisor.builder(chatMemory).build()` | 旧版：`new MessageChatMemoryAdvisor(chatMemory)` |
| 参数注入 | `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))` | 或 `ChatClient.builder(...).defaultAdvisors(...)` + request 级 param |
| `Message` 实现 | `UserMessage` / `AssistantMessage`（`org.springframework.ai.chat.messages`） | — |

> 本文档中的代码为方案示意，**未经过编译验证**（当前环境无法执行命令：沙箱内 `pwsh` 启动即失败 `0xC0000142`）。

---

# 附：落地调整（已实现）

第 1–3 步已按本文实现，落地时相对上面的示意改了两处，先看这里再看正文。

## A1. 用显式 `ChatMemory.get/add`，不用 `MessageChatMemoryAdvisor`

原设计把 advisor 挂在 `ChatClient` 上。落地时改成在 `AgentController` 里显式读写：

```java
String reply = deepSeek.answer(systemPrompt(context), chatMemory.get(conversationId), userPrompt(intent, request, context));
chatMemory.add(conversationId, List.of(new UserMessage(request.message()), new AssistantMessage(reply)));
```

原因：**"发给模型的上下文"和"存进记忆的内容"必须不同**。

- 发给模型的本轮 user 消息要带 `当前意图 / 用户源码 / 公开判题状态`，否则模型定位不了错误；
- 但存进记忆的只能是**干净的问答对**。advisor 会把 `.user(...)` 的内容原样落库，于是历史里每一轮都重复带一份源码（源码上限 100000 字符），一次就把 `max-chars` 预算吃光，记忆等于失效。
- 另外 `agent_messages` 同时是审计表，写库 owner 必须唯一且确定；显式写入不依赖 advisor 的保存语义（它保存 user 轮还是只保存 assistant 轮，各版本行为不完全一致）。

`ChatMemory` 仍然是 Spring AI 的抽象（`ChatMemory` + `MessageWindowChatMemory` + `ChatMemoryRepository`），只是不挂 advisor。若日后想换回 advisor，把 `chatMemory.get/add` 两行删掉、给 `ChatClient.Builder` 加 `defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())`、并把本轮上下文从 user 消息移到 system 即可。

## A2. 被拦截轮次与规则兜底改为 `recordAuditOnly`

Controller 不再直接 `INSERT agent_messages`，表内写入只走 `AgentMessageRepository`：

| 轮次类型 | 写入口 | `in_memory` | `safety_status` |
|---|---|---|---|
| 正常模型轮次 | `saveAll`（由 `ChatMemory.add` 触发） | 1 | `PASSED` |
| Safety 拦截 | `recordAuditOnly` | 0 | `BLOCKED` |
| 模型不可用时的规则兜底 | `recordAuditOnly` | 0 | `PASSED` |

## A3. 实际改动文件

| 类型 | 文件 |
|---|---|
| 新增 | `backend/.../db/migration/V17__agent_memory_order.sql` |
| 新增 | `backend/.../server/agent/AgentMessageRepository.java`（`ChatMemoryRepository` 的 JDBC 实现 + `trim` 纯函数） |
| 新增 | `backend/.../server/agent/AgentMemoryConfig.java`（`ChatMemory` bean） |
| 新增 | `backend/.../test/.../agent/AgentMessageRepositoryTest.java`（4 个 `trim` 单测，不依赖 DB） |
| 修改 | `backend/.../server/agent/AgentController.java`（删 `history()`、删两条 `agent_messages` 写入、接 `ChatMemory`） |
| 修改 | `backend/.../server/agent/DeepSeekClient.java`（新增 `answer(system, history, user)` 重载） |
| 修改 | `backend/.../resources/application.yml`（新增 `app.agent.memory.*`） |

## A4. 仍需编译验证的点（我无法执行命令）

1. `ChatMemoryRepository` 的四个方法签名是否与 1.0.0 一致；
2. `MessageWindowChatMemory.builder().chatMemoryRepository(r).maxMessages(n).build()` 的 builder 写法；
3. `ChatClientRequestSpec#messages(Message...)`（代码用的是变参形式 `history.toArray(Message[]::new)`）；
4. `Message#getText()` 在 1.0.0 中的可用性；
5. `spring.ai.openai` 空 `api-key` 时是否影响启动；
6. `org.springframework.ai.chat.memory.*` 是否已由 `spring-ai-starter-model-openai` 传递进来（未传递则需补 `spring-ai-client-chat` 依赖）。

**第 4、5 步（配置收敛、前端取值 bug）本次未实现。**

