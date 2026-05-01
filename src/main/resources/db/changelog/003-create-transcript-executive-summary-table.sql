--liquibase formatted sql

--changeset opencode:create-transcript-executive-summary-table
CREATE TABLE transcript_executive_summary (
    id UUID PRIMARY KEY,
    transcript_with_embeddings_id UUID NOT NULL REFERENCES transcript_with_embeddings(id),
    result TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);