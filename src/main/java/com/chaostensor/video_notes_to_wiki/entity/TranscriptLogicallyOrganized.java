package com.chaostensor.video_notes_to_wiki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("transcript_logically_organized")
public class TranscriptLogicallyOrganized {

    @Id
    private UUID id;
    private UUID transccriptRawId;
    private LlmStatus status;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors, getters, setters

    public TranscriptLogicallyOrganized() {}

    public TranscriptLogicallyOrganized(UUID id, UUID transcriptId, LlmStatus status, String result, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.transccriptRawId = transcriptId;
        this.status = status;
        this.result = result;
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

    public UUID getTransccriptRawId() {
        return transccriptRawId;
    }

    public void setTransccriptRawId(UUID transccriptRawId) {
        this.transccriptRawId = transccriptRawId;
    }

    public LlmStatus getStatus() {
        return status;
    }

    public void setStatus(LlmStatus status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
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