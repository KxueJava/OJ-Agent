ALTER TABLE submissions ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'SUBMIT' AFTER language;
CREATE INDEX idx_submissions_user_problem ON submissions (user_id, problem_id, created_at DESC);
