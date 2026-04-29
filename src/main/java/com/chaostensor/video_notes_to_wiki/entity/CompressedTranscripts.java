package com.chaostensor.video_notes_to_wiki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("compressed_transcripts")
public class CompressedTranscripts {

    @Id
    private UUID id;
    private String compressedResult;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors, getters, setters

    public CompressedTranscripts() {}

    public CompressedTranscripts(UUID id, String compressedResult, LocalDateTime createdAt, LocalDateTime updatedAt) {
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