package com.chaostensor.video_notes_to_wiki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("wiki_ready_transcript")
public class WikiReadyTranscript {

    @Id
    private UUID id;
    private UUID simplifiedTranscriptId;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors, getters, setters

    public WikiReadyTranscript() {}

    public WikiReadyTranscript(UUID id, UUID simplifiedTranscriptId, String result, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.simplifiedTranscriptId = simplifiedTranscriptId;
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

    public UUID getSimplifiedTranscriptId() {
        return simplifiedTranscriptId;
    }

    public void setSimplifiedTranscriptId(UUID simplifiedTranscriptId) {
        this.simplifiedTranscriptId = simplifiedTranscriptId;
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