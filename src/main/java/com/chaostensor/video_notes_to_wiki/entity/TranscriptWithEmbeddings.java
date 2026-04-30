package com.chaostensor.video_notes_to_wiki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Table("transcript_with_embeddings")
public class TranscriptWithEmbeddings {

    @Id
    private UUID id;
    private UUID transcriptRawId;
    private List<String> chunks;
    private List<List<Float>> embeddings;  // List of embedding vectors
    private LlmStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors, getters, setters

    public TranscriptWithEmbeddings() {}

    public TranscriptWithEmbeddings(UUID id, UUID transcriptRawId, List<String> chunks,
                                  List<List<Float>> embeddings, LlmStatus status,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.transcriptRawId = transcriptRawId;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTranscriptRawId() {
        return transcriptRawId;
    }

    public void setTranscriptRawId(UUID transcriptRawId) {
        this.transcriptRawId = transcriptRawId;
    }

    public List<String> getChunks() {
        return chunks;
    }

    public void setChunks(List<String> chunks) {
        this.chunks = chunks;
    }

    public List<List<Float>> getEmbeddings() {
        return embeddings;
    }

    public void setEmbeddings(List<List<Float>> embeddings) {
        this.embeddings = embeddings;
    }

    public LlmStatus getStatus() {
        return status;
    }

    public void setStatus(LlmStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}