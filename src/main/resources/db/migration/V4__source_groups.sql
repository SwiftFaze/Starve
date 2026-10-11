CREATE TABLE groups (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE
);

ALTER TABLE sources ADD COLUMN group_id INTEGER REFERENCES groups (id) ON DELETE SET NULL;

CREATE INDEX idx_sources_group ON sources (group_id);
CREATE INDEX idx_items_source_read ON items (source_id, read_at);
