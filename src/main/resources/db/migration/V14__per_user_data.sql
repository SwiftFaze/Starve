-- Every source and group belongs to one user. SQLite cannot add a NOT NULL foreign key to a table, so groups,
-- sources and items are rebuilt (ids are kept, so item_search rows and the group and source links stay valid).
-- Existing rows, if any, go to the first account; a database that holds rows but no account cannot be upgraded.
-- items is rebuilt as well because dropping sources would otherwise cascade-delete its items.
CREATE TABLE groups_new (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name     TEXT    NOT NULL COLLATE NOCASE,
    UNIQUE (owner_id, name)
);

INSERT INTO groups_new (id, owner_id, name)
SELECT id, (SELECT MIN(id) FROM users), name FROM groups;

CREATE TABLE sources_new (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id           INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    url                TEXT    NOT NULL,
    title              TEXT    NOT NULL,
    etag               TEXT,
    last_modified      TEXT,
    last_fetched_at    INTEGER,
    last_error         TEXT,
    group_id           INTEGER REFERENCES groups_new (id) ON DELETE SET NULL,
    favicon_url        TEXT,
    favicon_checked_at INTEGER,
    UNIQUE (owner_id, url)
);

INSERT INTO sources_new (id, owner_id, url, title, etag, last_modified, last_fetched_at, last_error, group_id,
                         favicon_url, favicon_checked_at)
SELECT id, (SELECT MIN(id) FROM users), url, title, etag, last_modified, last_fetched_at, last_error, group_id,
       favicon_url, favicon_checked_at
FROM sources;

CREATE TABLE items_new (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id          INTEGER NOT NULL REFERENCES sources_new (id) ON DELETE CASCADE,
    item_key           TEXT    NOT NULL,
    title              TEXT    NOT NULL,
    link               TEXT,
    published_at       INTEGER NOT NULL,
    author             TEXT,
    thumbnail_url      TEXT,
    first_seen_at      INTEGER NOT NULL,
    read_at            INTEGER,
    body               TEXT,
    oembed_json        TEXT,
    favourited_at      INTEGER,
    article_checked_at INTEGER,
    later_at           INTEGER,
    UNIQUE (source_id, item_key)
);

INSERT INTO items_new (id, source_id, item_key, title, link, published_at, author, thumbnail_url, first_seen_at,
                       read_at, body, oembed_json, favourited_at, article_checked_at, later_at)
SELECT id, source_id, item_key, title, link, published_at, author, thumbnail_url, first_seen_at,
       read_at, body, oembed_json, favourited_at, article_checked_at, later_at
FROM items;

DROP TABLE items;
DROP TABLE sources;
DROP TABLE groups;

ALTER TABLE groups_new RENAME TO groups;
ALTER TABLE sources_new RENAME TO sources;
ALTER TABLE items_new RENAME TO items;

CREATE INDEX idx_sources_group ON sources (group_id);
CREATE INDEX idx_items_first_seen ON items (first_seen_at);
CREATE INDEX idx_items_timeline ON items (published_at DESC, id DESC);
CREATE INDEX idx_items_source_read ON items (source_id, read_at);

-- Deleting an owner cascades through sources to items, which bypasses the application code that keeps the
-- full-text index in step, so the index row goes with the item whichever way it is deleted.
CREATE TRIGGER items_search_cleanup AFTER DELETE ON items
BEGIN
    DELETE FROM item_search WHERE rowid = old.id;
END;
