CREATE TABLE sources (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    url             TEXT    NOT NULL UNIQUE,
    title           TEXT    NOT NULL,
    etag            TEXT,
    last_modified   TEXT,
    last_fetched_at INTEGER,
    last_error      TEXT
);

CREATE TABLE items (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id     INTEGER NOT NULL REFERENCES sources (id) ON DELETE CASCADE,
    item_key      TEXT    NOT NULL,
    title         TEXT    NOT NULL,
    link          TEXT,
    published_at  INTEGER NOT NULL,
    author        TEXT,
    thumbnail_url TEXT,
    first_seen_at INTEGER NOT NULL,
    UNIQUE (source_id, item_key)
);

CREATE INDEX idx_items_first_seen ON items (first_seen_at);
