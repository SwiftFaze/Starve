-- A podcast episode's audio enclosure and its length in seconds, both NULL for other items and for episodes stored
-- before this (ingestion only inserts new items, so those keep linking out).
ALTER TABLE items ADD COLUMN audio_url TEXT;
ALTER TABLE items ADD COLUMN audio_duration INTEGER;
