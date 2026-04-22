package com.chaostensor.video_notes_to_wiki.controller;

import java.util.UUID;

public class SimplifiedTranscriptResponse {
    private UUID simplifiedTranscriptId;

    public SimplifiedTranscriptResponse() {}

    public SimplifiedTranscriptResponse(UUID simplifiedTranscriptId) {
        this.simplifiedTranscriptId = simplifiedTranscriptId;
    }

    public UUID getSimplifiedTranscriptId() {
        return simplifiedTranscriptId;
    }

    public void setSimplifiedTranscriptId(UUID simplifiedTranscriptId) {
        this.simplifiedTranscriptId = simplifiedTranscriptId;
    }
}