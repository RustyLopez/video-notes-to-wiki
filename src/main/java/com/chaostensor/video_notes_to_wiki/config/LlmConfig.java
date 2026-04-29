package com.chaostensor.video_notes_to_wiki.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.llm")
@Data
public class LlmConfig {
    private int contextWindowTokens = 4096; // Default for GPT-3.5
    private int maxChunkTokens = 4096;
    private double reductionRatio = 0.3;
    private int threadPoolSize = 5;
    private long maxMemoryUsageMb = 1024; // 1GB default
    private int promptOverheadTokens = 500; // Estimate for prompt + result buffer
}