-- V2__folders_boards_and_feed_folder.sql

CREATE TABLE folder (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE feed ADD COLUMN folder_id BIGINT REFERENCES folder(id) ON DELETE SET NULL;
CREATE INDEX idx_feed_folder_id ON feed(folder_id);

CREATE TABLE board (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE board_article (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL REFERENCES board(id) ON DELETE CASCADE,
    article_id BIGINT NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (board_id, article_id)
);

CREATE INDEX idx_board_article_board_id ON board_article(board_id);
CREATE INDEX idx_board_article_article_id ON board_article(article_id);
