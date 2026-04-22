package com.chaostensor.video_notes_to_wiki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Table("wiki_ready_transcript_completion_group")
public class WikiReadyTranscriptCompletionGroup {

    @Id
    private UUID id;
    private UUID jobId;
    private String transcriptSubIdsJson; // JSON string containing list of transcriptSubIds
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors, getters, setters

    public WikiReadyTranscriptCompletionGroup() {}

    public WikiReadyTranscriptCompletionGroup(UUID id, UUID jobId, String transcriptSubIdsJson, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.jobId = jobId;
        this.transcriptSubIdsJson = transcriptSubIdsJson;
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

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getTranscriptSubIdsJson() {
        return transcriptSubIdsJson;
    }

    public void setTranscriptSubIdsJson(String transcriptSubIdsJson) {
        this.transcriptSubIdsJson = transcriptSubIdsJson;
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