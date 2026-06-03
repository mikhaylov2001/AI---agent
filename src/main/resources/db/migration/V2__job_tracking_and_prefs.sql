-- Baseline for existing deployments (tables created via JPA ddl-auto previously)
-- Flyway baseline-on-migrate handles first run on prod.

-- V2: job tracking, search prefs, persisted sessions
ALTER TABLE users ADD COLUMN IF NOT EXISTS hh_search_area VARCHAR(32);
ALTER TABLE users ADD COLUMN IF NOT EXISTS search_experience VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS search_remote BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS onboarding_done BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS session_state VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS session_payload TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_cover_letter TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_cover_vacancy_id VARCHAR(32);

CREATE TABLE IF NOT EXISTS job_applications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vacancy_id VARCHAR(32) NOT NULL,
    vacancy_title VARCHAR(512),
    company VARCHAR(256),
    vacancy_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'SEEN',
    match_score INTEGER,
    cover_letter TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    applied_at TIMESTAMP,
    UNIQUE (user_id, vacancy_id)
);

CREATE INDEX IF NOT EXISTS idx_job_applications_user ON job_applications(user_id);
