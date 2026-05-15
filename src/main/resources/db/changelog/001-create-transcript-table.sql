--liquibase formatted sql

--changeset opencode:create-transcriptRaw-table
CREATE TABLE transcript_raw (
    id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    video_path VARCHAR(500) NOT NULL,
    hash VARCHAR(64) NOT NULL,
    transcript_raw TEXT DEFAULT NULL, -- TODO should be nullable
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX idx_transcript_raw_video_path_hash ON transcript_raw (video_path, hash);

--rollback DROP INDEX idx_transcript_raw_video_path_hash;
--rollback DROP TABLE transcript_raw CASCADE;