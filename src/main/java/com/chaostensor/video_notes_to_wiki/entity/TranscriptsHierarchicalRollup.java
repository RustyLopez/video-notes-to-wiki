package com.chaostensor.video_notes_to_wiki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * NOTE may be very large we  probably want to .. partition and expire rows here. Could get away
 * with only saving the latest since it's all derived but, may be nice to have some history.
 */
@Table("transcripts_hierarchical_rollup")
public class TranscriptsHierarchicalRollup {

    @Id
    private UUID id;
    private String compressedResult;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors, getters, setters

    public TranscriptsHierarchicalRollup() {}

    public TranscriptsHierarchicalRollup(UUID id, String compressedResult, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.compressedResult = compressedResult;
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

    public String getCompressedResult() {
        return compressedResult;
    }

    public void setCompressedResult(String compressedResult) {
        this.compressedResult = compressedResult;
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