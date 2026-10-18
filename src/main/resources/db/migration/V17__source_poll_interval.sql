-- How often a source is fetched, in minutes, chosen from a fixed set; null means the global default
-- (starve.poll.interval).
ALTER TABLE sources ADD COLUMN poll_interval_minutes INTEGER;
