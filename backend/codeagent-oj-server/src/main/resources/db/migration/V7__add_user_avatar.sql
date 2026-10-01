ALTER TABLE users ADD COLUMN avatar_url VARCHAR(255) NULL AFTER display_name;
ALTER TABLE users ADD COLUMN avatar_color VARCHAR(16) NOT NULL DEFAULT 'orange' AFTER avatar_url;
