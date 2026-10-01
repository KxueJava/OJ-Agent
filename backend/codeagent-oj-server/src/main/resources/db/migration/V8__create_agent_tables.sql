CREATE TABLE agent_sessions (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    problem_version_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_agent_sessions_version FOREIGN KEY (problem_version_id) REFERENCES problem_versions (id),
    INDEX idx_agent_sessions_user (user_id, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_messages (
    id BIGINT NOT NULL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    agent VARCHAR(32) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    safety_status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_messages_session FOREIGN KEY (session_id) REFERENCES agent_sessions (id),
    INDEX idx_agent_messages_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_audits (
    id BIGINT NOT NULL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    intent VARCHAR(32) NOT NULL,
    route VARCHAR(32) NOT NULL,
    safety_status VARCHAR(16) NOT NULL,
    blocked_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_audits_session FOREIGN KEY (session_id) REFERENCES agent_sessions (id),
    INDEX idx_agent_audits_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
