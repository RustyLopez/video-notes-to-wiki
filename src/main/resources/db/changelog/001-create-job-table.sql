--liquibase formatted sql

--changeset opencode:create-job-table
CREATE TABLE job (
    id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    input_dir VARCHAR(500) NOT NULL,
    transcripts JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);