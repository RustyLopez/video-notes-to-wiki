package com.chaostensor.video_notes_to_wiki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("simplified_transcript")
public class SimplifiedTranscript {

    @Id
    private UUID id;
    private UUID jobId;
    private String transcriptSubId;
    private SimplifiedTranscriptStatus status;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors, getters, setters

    public SimplifiedTranscript() {}

    public SimplifiedTranscript(UUID id, UUID jobId, String transcriptSubId, SimplifiedTranscriptStatus status, String result, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.jobId = jobId;
        this.transcriptSubId = transcriptSubId;
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

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getTranscriptSubId() {
        return transcriptSubId;
    }

    public void setTranscriptSubId(String transcriptSubId) {
        this.transcriptSubId = transcriptSubId;
    }

    public SimplifiedTranscriptStatus getStatus() {
        return status;
    }

    public void setStatus(SimplifiedTranscriptStatus status) {
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