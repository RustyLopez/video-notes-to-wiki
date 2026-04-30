--liquibase formatted sql

--changeset opencode:create-transcriptRaw-table
CREATE TABLE transcript_raw (
    id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    video_path VARCHAR(500) NOT NULL,
    transcript_raw TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);