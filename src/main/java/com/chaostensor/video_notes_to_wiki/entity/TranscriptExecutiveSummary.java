package com.chaostensor.video_notes_to_wiki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("transcript_executive_summary")
public class TranscriptExecutiveSummary {

    @Id
    private UUID id;
    private UUID transcriptLogicallyOrganizedId;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors, getters, setters

    public TranscriptExecutiveSummary() {}

    public TranscriptExecutiveSummary(UUID id, UUID transcriptLogicallyOrganizedId, String result, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.transcriptLogicallyOrganizedId = transcriptLogicallyOrganizedId;
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

    public UUID getTranscriptLogicallyOrganizedId() {
        return transcriptLogicallyOrganizedId;
    }

    public void setTranscriptLogicallyOrganizedId(UUID transcriptLogicallyOrganizedId) {
        this.transcriptLogicallyOrganizedId = transcriptLogicallyOrganizedId;
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