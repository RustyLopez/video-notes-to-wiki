--liquibase formatted sql

--changeset opencode:add-hash-to-transcript-raw
ALTER TABLE transcript_raw ADD COLUMN hash VARCHAR(64);

CREATE UNIQUE INDEX idx_transcript_raw_video_path_hash ON transcript_raw (video_path, hash);