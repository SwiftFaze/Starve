ALTER TABLE items ADD COLUMN read_at INTEGER;

CREATE INDEX idx_items_timeline ON items (published_at DESC, id DESC);
