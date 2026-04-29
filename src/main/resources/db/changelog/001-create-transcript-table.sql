--liquibase formatted sql

--changeset opencode:create-transcript-table
CREATE TABLE transcript (
    id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    video_path VARCHAR(500) NOT NULL,
    transcript TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);