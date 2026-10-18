-- Keyword tags: each user's saved keyword views across sources. Items are matched at query time through item_search,
-- so nothing is stored per item. terms holds the comma-separated terms as the user entered them, after checking.
-- Deleting the limiting group removes the limit; deleting the user deletes their tags.
CREATE TABLE tags (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id   INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL COLLATE NOCASE,
    terms      TEXT    NOT NULL,
    match_body INTEGER NOT NULL DEFAULT 0,
    group_id   INTEGER REFERENCES groups (id) ON DELETE SET NULL,
    UNIQUE (owner_id, name)
);
