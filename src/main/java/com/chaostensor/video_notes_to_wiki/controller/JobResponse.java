package com.chaostensor.video_notes_to_wiki.controller;

import java.util.UUID;

public class JobResponse {
    private UUID jobId;

    public JobResponse() {}

    public JobResponse(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }
}