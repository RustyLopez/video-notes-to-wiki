package com.chaostensor.video_notes_to_wiki.controller;

import java.util.UUID;

public class JobRequest {
    private String inputDir;

    public JobRequest() {}

    public JobRequest(String inputDir) {
        this.inputDir = inputDir;
    }

    public String getInputDir() {
        return inputDir;
    }

    public void setInputDir(String inputDir) {
        this.inputDir = inputDir;
    }
}