-- V7__engagement.sql: the v0.3.0 schema (engagement capture + the Phase 11 suggestion dismissal). No backfill.

-- One sticky row per article and kind. Forget deletes the rows; an article or feed delete cascades.
CREATE TABLE article_engagement (
    article_id BIGINT NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('OPEN_ORIGINAL', 'STAR', 'BOARD', 'RAINDROP')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), -- kept so decay stays possible later
    PRIMARY KEY (article_id, kind)
);

-- A handled suggestion stays handled (D-14). There is no topic column and no reference to interest_topic,
-- so deleting a topic never touches this row.
CREATE TABLE topic_suggestion_dismissal (
    article_id BIGINT PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    reason TEXT NOT NULL CHECK (reason IN ('DISMISSED', 'TOPIC_CREATED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
