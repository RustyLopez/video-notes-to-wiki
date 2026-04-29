package com.chaostensor.video_notes_to_wiki.controller;

import java.util.UUID;

public class TranscribeResponse {
    private UUID transcriptId;
    private String transcript;

    public TranscribeResponse() {}

    public TranscribeResponse(UUID transcriptId, String transcript) {
        this.transcriptId = transcriptId;
        this.transcript = transcript;
    }

    public UUID getTranscriptId() {
        return transcriptId;
    }

    public void setTranscriptId(UUID transcriptId) {
        this.transcriptId = transcriptId;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }
}