package com.chaostensor.video_notes_to_wiki.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.chunking")
@Data
public class ChunkingConfig {
    private int minChunkSize = 150;  // approximate length of a single sentence
    private int maxChunkSize = 1024;  // small enough for several chunks to fit in context window
    private int minChunksForRollup = 4;
}