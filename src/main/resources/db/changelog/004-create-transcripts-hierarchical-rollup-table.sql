--liquibase formatted sql

--changeset opencode:create-transcripts-hierarchical-rollup-table
CREATE TABLE transcripts_hierarchical_rollup (
    id UUID PRIMARY KEY,
    compressed_result TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    chunks_with_embeddings JSONB
);