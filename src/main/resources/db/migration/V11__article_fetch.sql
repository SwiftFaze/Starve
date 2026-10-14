-- Article fetch attempts for teaser-only posts; NULL until the reader first tries the item.
ALTER TABLE items ADD COLUMN article_checked_at INTEGER;
