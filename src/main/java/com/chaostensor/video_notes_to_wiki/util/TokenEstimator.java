package com.chaostensor.video_notes_to_wiki.util;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {

    // Rough estimation: 1 token ≈ 4 characters for English text
    private static final int CHARS_PER_TOKEN = 4;

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    public int estimateWordCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\\s+").length;
    }
}