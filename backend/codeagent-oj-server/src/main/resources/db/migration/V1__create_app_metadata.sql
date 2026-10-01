CREATE TABLE app_metadata (
    metadata_key VARCHAR(100) NOT NULL PRIMARY KEY,
    metadata_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO app_metadata (metadata_key, metadata_value)
VALUES ('schema_version', '1');
