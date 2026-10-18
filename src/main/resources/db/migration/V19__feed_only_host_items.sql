-- GitHub and Bluesky items had their JavaScript-rendered page fetched as if they were teasers, storing page chrome
-- as the body and the account avatar as the thumbnail. Their feed entry is the whole post, so those items lose the
-- fetched body, thumbnail and check, and the next poll fills body and thumbnail back in from the feed. The hosts are
-- starve.reader.feed-only-hosts' defaults at the time of this migration.
CREATE TEMPORARY TABLE feed_only_items AS
SELECT id FROM items
WHERE article_checked_at IS NOT NULL
  AND (lower(link) LIKE 'http://github.com/%' OR lower(link) LIKE 'https://github.com/%'
    OR lower(link) LIKE 'http://www.github.com/%' OR lower(link) LIKE 'https://www.github.com/%'
    OR lower(link) LIKE 'http://bsky.app/%' OR lower(link) LIKE 'https://bsky.app/%');

UPDATE item_search SET body = '' WHERE rowid IN (SELECT id FROM feed_only_items);

UPDATE items SET body = NULL, thumbnail_url = NULL, article_checked_at = NULL
WHERE id IN (SELECT id FROM feed_only_items);

DROP TABLE feed_only_items;
