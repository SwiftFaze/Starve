-- Where the owner stopped playing an item, in seconds, so it resumes there on any device; NULL when it was never
-- played, was barely started or was played to the end. Items are per user, so the position is too.
ALTER TABLE items ADD COLUMN play_position INTEGER;
