package com.chaostensor.video_notes_to_wiki.config;

import com.chaostensor.video_notes_to_wiki.util.TokenEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryValidator implements CommandLineRunner {

    private final LlmConfig llmConfig;
    private final TokenEstimator tokenEstimator;

    @Override
    public void run(String... args) throws Exception {
        validateMemoryConstraints();
    }

    private void validateMemoryConstraints() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        long maxMemoryMb = maxMemory / (1024 * 1024);

        long requiredMemoryMb = Math.max(
            llmConfig.getContextWindowTokens() * 2L, // Rough estimate: 2 bytes per token for storage
            llmConfig.getMaxMemoryUsageMb()
        );

        if (maxMemoryMb < requiredMemoryMb) {
            log.error("Insufficient heap memory. Available: {} MB, Required: {} MB", maxMemoryMb, requiredMemoryMb);
            throw new IllegalStateException("Insufficient memory for LLM operations");
        }

        log.info("Memory validation passed. Available: {} MB, Required: {} MB", maxMemoryMb, requiredMemoryMb);
    }
}