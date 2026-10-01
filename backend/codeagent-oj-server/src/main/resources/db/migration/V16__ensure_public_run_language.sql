SET @has_language = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'public_runs'
      AND column_name = 'language'
);
SET @language_sql = IF(@has_language = 0,
    'ALTER TABLE public_runs ADD COLUMN language VARCHAR(16) NOT NULL DEFAULT ''JAVA_21'' AFTER problem_version_id',
    'SELECT 1');
PREPARE language_stmt FROM @language_sql;
EXECUTE language_stmt;
DEALLOCATE PREPARE language_stmt;
