CREATE TABLE public_runs (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    problem_version_id BIGINT NOT NULL,
    source_code MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    verdict_message VARCHAR(500) NULL,
    runtime_ms INT NULL,
    memory_kb INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    CONSTRAINT fk_public_runs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_public_runs_version FOREIGN KEY (problem_version_id) REFERENCES problem_versions (id),
    INDEX idx_public_runs_user (user_id, problem_version_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE public_run_cases (
    id BIGINT NOT NULL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    example_id BIGINT NOT NULL,
    verdict VARCHAR(16) NOT NULL,
    runtime_ms INT NULL,
    output_summary VARCHAR(500) NULL,
    CONSTRAINT fk_public_run_cases_run FOREIGN KEY (run_id) REFERENCES public_runs (id),
    CONSTRAINT fk_public_run_cases_example FOREIGN KEY (example_id) REFERENCES examples (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
