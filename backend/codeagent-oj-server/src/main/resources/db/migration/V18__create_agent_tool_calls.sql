-- Agent 工具调用审计：可回放"模型当时调了哪些工具、拿到多少内容、耗时多少"
CREATE TABLE agent_tool_calls (
    id BIGINT NOT NULL PRIMARY KEY,
    session_id BIGINT NULL,
    tool VARCHAR(48) NOT NULL,
    args_json VARCHAR(1024) NULL,
    result_chars INT NULL,
    ok TINYINT(1) NOT NULL,
    latency_ms INT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_agent_tool_calls_session FOREIGN KEY (session_id) REFERENCES agent_sessions (id),
    INDEX idx_agent_tool_calls_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
