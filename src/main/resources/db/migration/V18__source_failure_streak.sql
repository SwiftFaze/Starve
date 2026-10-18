-- When the source's current run of failed fetches began (epoch millis), null while its last fetch succeeded: a source
-- failing for 24 hours is broken.
ALTER TABLE sources ADD COLUMN failing_since INTEGER;

-- Where a permanent redirect moved the feed when that URL could not be adopted because the owner already follows it;
-- null otherwise.
ALTER TABLE sources ADD COLUMN moved_to TEXT;
