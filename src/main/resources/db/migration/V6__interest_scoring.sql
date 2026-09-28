-- V6__interest_scoring.sql: the complete interest-scoring schema. Later phases add no migrations.

-- Singleton profile (single-user app), seeded so it is always UPDATE-able via save().
CREATE TABLE interest_profile (
    id INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    profile_text TEXT NOT NULL DEFAULT '',
    version INTEGER NOT NULL DEFAULT 1, -- bumped only when profile_text changes
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO interest_profile (id) VALUES (1);

CREATE TABLE interest_topic (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL, -- short display label; never sent to Jev
    description TEXT NOT NULL, -- goes into the Noul instructions
    weight INTEGER NOT NULL DEFAULT 20 CHECK (weight BETWEEN -50 AND 50), -- points (R1, D-10)
    version INTEGER NOT NULL DEFAULT 1, -- bumped only when description changes
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One row per judged article. SCORED is write-once; FAILED counts attempts; SKIPPED is terminal (R3).
CREATE TABLE article_score (
    article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('SCORED', 'FAILED', 'SKIPPED')),
    profile_score DOUBLE PRECISION, -- raw Score value in [0, profile_max_level]; NULL when no profile question
    profile_max_level INTEGER, -- levels - 1 at scoring time
    profile_confidence DOUBLE PRECISION,
    profile_version INTEGER,
    model TEXT, -- model reported in the Jev response body
    request_id TEXT,
    attempts INTEGER NOT NULL DEFAULT 1,
    last_error TEXT,
    scored_at TIMESTAMPTZ NOT NULL DEFAULT NOW() -- time of the last write (score or failed attempt)
);

-- Child of article_score, so deleting a score row (Phase 4 "Re-score unread") also removes its nouls.
CREATE TABLE article_topic_score (
    article_id BIGINT NOT NULL REFERENCES article_score(article_id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES interest_topic(id) ON DELETE CASCADE,
    noul DOUBLE PRECISION NOT NULL CHECK (noul BETWEEN 0 AND 1),
    topic_version INTEGER NOT NULL,
    PRIMARY KEY (article_id, topic_id)
);
CREATE INDEX idx_article_topic_score_topic ON article_topic_score(topic_id);

-- D-01/D-02: one vote per article, optional narrowing to picked topics.
-- When topics_narrowed is true only the article_feedback_topic child rows count,
-- and zero child rows penalize nothing (D-02).
CREATE TABLE article_feedback (
    article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    vote SMALLINT NOT NULL CHECK (vote IN (-1, 1)),
    topics_narrowed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- D-03: no CHECK ties picked topics to the vote direction.
CREATE TABLE article_feedback_topic (
    article_id BIGINT NOT NULL REFERENCES article_feedback(article_id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES interest_topic(id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, topic_id)
);
CREATE INDEX idx_article_feedback_topic_topic ON article_feedback_topic(topic_id);
