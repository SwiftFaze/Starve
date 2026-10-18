-- When an item was actually opened (title link, reader, detail view or player), apart from read state, so that
-- marking read without opening can be told apart as a skip. Null means never opened.
ALTER TABLE items ADD COLUMN opened_at INTEGER;

-- Reads before this instant were never tracked, so they cannot count as skips (epoch milliseconds).
INSERT INTO app_metadata (key, value) VALUES ('opens_tracked_since', CAST(strftime('%s', 'now') AS INTEGER) * 1000);
