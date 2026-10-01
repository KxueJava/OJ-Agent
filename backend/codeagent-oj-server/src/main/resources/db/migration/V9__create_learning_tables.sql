CREATE TABLE learning_events (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NULL,
    submission_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    payload_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_learning_events_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_learning_events_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_learning_events_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    INDEX idx_learning_events_user (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mistake_books (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    error_type VARCHAR(32) NOT NULL,
    diagnosis VARCHAR(500) NOT NULL,
    review_note VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    CONSTRAINT fk_mistake_books_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_mistake_books_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_mistake_books_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    UNIQUE KEY uk_mistake_submission (user_id, submission_id),
    INDEX idx_mistake_books_user (user_id, status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE study_plans (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_date DATE NOT NULL,
    target_count INT NOT NULL DEFAULT 3,
    completed_count INT NOT NULL DEFAULT 0,
    recommendation_json JSON NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_study_plans_user FOREIGN KEY (user_id) REFERENCES users (id),
    UNIQUE KEY uk_study_plan_day (user_id, plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
