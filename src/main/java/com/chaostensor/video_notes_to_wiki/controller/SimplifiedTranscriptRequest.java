package com.chaostensor.video_notes_to_wiki.controller;

import java.util.UUID;

public class SimplifiedTranscriptRequest {
    private UUID jobId;
    private String transcriptSubId;

    public SimplifiedTranscriptRequest() {}

    public SimplifiedTranscriptRequest(UUID jobId, String transcriptSubId) {
        this.jobId = jobId;
        this.transcriptSubId = transcriptSubId;
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
}