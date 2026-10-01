CREATE TABLE submissions (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    problem_version_id BIGINT NOT NULL,
    language VARCHAR(16) NOT NULL,
    source_code MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    verdict_message VARCHAR(500) NULL,
    runtime_ms INT NULL,
    memory_kb INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_submissions_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_submissions_version FOREIGN KEY (problem_version_id) REFERENCES problem_versions (id),
    INDEX idx_submissions_user (user_id, created_at DESC),
    INDEX idx_submissions_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE submission_cases (
    id BIGINT NOT NULL PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    test_case_id BIGINT NOT NULL,
    verdict VARCHAR(16) NOT NULL,
    runtime_ms INT NULL,
    memory_kb INT NULL,
    output_summary VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_submission_cases_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_submission_cases_test FOREIGN KEY (test_case_id) REFERENCES test_cases (id),
    UNIQUE KEY uk_submission_case (submission_id, test_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_events (
    id BIGINT NOT NULL PRIMARY KEY,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_outbox_pending (status, available_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
