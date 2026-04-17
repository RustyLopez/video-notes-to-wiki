package com.chaostensor.video_notes_to_wiki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("job")
public class Job {

    @Id
    private UUID id;
    private JobStatus status;
    private String inputDir;
    private String transcriptsJson; // JSON string containing list of transcripts
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors, getters, setters

    public Job() {}

    public Job(UUID id, JobStatus status, String inputDir, String transcriptsJson, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.status = status;
        this.inputDir = inputDir;
        this.transcriptsJson = transcriptsJson;
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

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getInputDir() {
        return inputDir;
    }

    public void setInputDir(String inputDir) {
        this.inputDir = inputDir;
    }

    public String getTranscriptsJson() {
        return transcriptsJson;
    }

    public void setTranscriptsJson(String transcriptsJson) {
        this.transcriptsJson = transcriptsJson;
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