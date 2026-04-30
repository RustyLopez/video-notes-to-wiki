--liquibase formatted sql

--changeset opencode:create-transcript-with-embeddings-table
CREATE TABLE transcript_with_embeddings (
    id UUID PRIMARY KEY,
    transcript_raw_id UUID NOT NULL REFERENCES transcript_raw(id),
    chunks JSONB NOT NULL,  -- Array of text chunks
    embeddings JSONB NOT NULL,  -- Array of embeddings corresponding to chunks
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);