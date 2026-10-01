-- Agent 诊断结果：一次提交对应一条 DIAGNOSIS（唯一键保证幂等），前端可直接消费
CREATE TABLE agent_findings (
    id BIGINT NOT NULL PRIMARY KEY,
    submission_id BIGINT NULL,
    session_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    problem_version_id BIGINT NULL,
    kind VARCHAR(24) NOT NULL,
    verdict VARCHAR(16) NULL,
    summary VARCHAR(512) NULL,
    content MEDIUMTEXT NOT NULL,
    model VARCHAR(64) NULL,
    latency_ms INT NULL,
    safety_status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_agent_findings_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_agent_findings_session FOREIGN KEY (session_id) REFERENCES agent_sessions (id),
    CONSTRAINT fk_agent_findings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_agent_findings_version FOREIGN KEY (problem_version_id) REFERENCES problem_versions (id),
    UNIQUE KEY uk_agent_findings (submission_id, kind),
    INDEX idx_agent_findings_user (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
