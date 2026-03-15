CREATE TABLE feed (
    id BIGSERIAL PRIMARY KEY,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    site_url TEXT,
    feed_type TEXT NOT NULL,
    poll_interval_minutes INTEGER NOT NULL DEFAULT 15,
    last_polled_at TIMESTAMPTZ,
    last_successful_poll_at TIMESTAMPTZ,
    error_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    etag TEXT,
    last_modified_header TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE article (
    id BIGSERIAL PRIMARY KEY,
    feed_id BIGINT NOT NULL REFERENCES feed(id) ON DELETE CASCADE,
    guid TEXT NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    author TEXT,
    content TEXT,
    summary TEXT,
    published_at TIMESTAMPTZ,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    starred BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (feed_id, guid)
);

CREATE INDEX idx_article_feed_id ON article(feed_id);
CREATE INDEX idx_article_published_at ON article(published_at DESC);
CREATE INDEX idx_article_read ON article(read) WHERE read = FALSE;
CREATE INDEX idx_article_starred ON article(starred) WHERE starred = TRUE;
CREATE INDEX idx_article_fetched_at ON article(fetched_at);

CREATE TABLE integration_config (
    id BIGSERIAL PRIMARY KEY,
    type TEXT NOT NULL UNIQUE,
    config TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
