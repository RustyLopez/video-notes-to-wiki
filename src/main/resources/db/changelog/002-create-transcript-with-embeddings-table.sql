--liquibase formatted sql

--changeset opencode:create-transcript-with-embeddings-table
CREATE TABLE transcript_with_embeddings (
    id UUID PRIMARY KEY,
    transcript_raw_id UUID NOT NULL REFERENCES transcript_raw(id),
    chunk_embeddings JSONB NOT NULL,  -- List of ChunkEmbedding objects
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--rollback DROP TABLE transcript_with_embeddings CASCADE;