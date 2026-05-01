--liquibase formatted sql

--changeset opencode:create-wiki-table
CREATE TABLE wiki (
    id UUID PRIMARY KEY,
    transcript_id UUID NOT NULL REFERENCES transcripts_hierarchical_rollup(id),
    result TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);