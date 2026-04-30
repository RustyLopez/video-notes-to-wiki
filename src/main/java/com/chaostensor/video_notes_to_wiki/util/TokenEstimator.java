package com.chaostensor.video_notes_to_wiki.util;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {

    // Rough estimation: 1 token ≈ 4 characters for English text
    /**
     * TODO this rough estimate is problematic.. because it's super fuzzy and we do need a hard cap output.
     *  WE can just truncate if we go over in token count. But we do need to be able to count the tokens
     *  so we need to ensure those tokens are.. per char. Which I'm nto 100% sure of yet. It may depend on the
     *  llm
     *
     */
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


    public int estimateMaxWordCountForMaxTokens(final int maxChunkTokens) {
        return maxChunkTokens * CHARS_PER_TOKEN;
    }
}