package com.chaostensor.video_notes_to_wiki.controller;

import java.util.UUID;

public class TranscribeRequest {
    private String videoPath;

    public TranscribeRequest() {}

    public TranscribeRequest(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }
}