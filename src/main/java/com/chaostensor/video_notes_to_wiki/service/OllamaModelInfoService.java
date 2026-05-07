package com.chaostensor.video_notes_to_wiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaModelInfoService {

    private final WebClient.Builder webClientBuilder;

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${app.llm.chat.models.preferred}")
    private String preferredChatModel;

    private Integer contextWindowTokens;

    @PostConstruct
    public void init() {
        try {
            WebClient webClient = webClientBuilder.baseUrl(ollamaBaseUrl).build();

            // Query Ollama /api/show for model info
            Map response = webClient.post()
                    .uri("/api/show")
                    .bodyValue(Map.of("name", preferredChatModel))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("modelfile")) {
                // The response might have modelfile, but context_length might be in model_info or elsewhere
                // For Ollama, context_length is typically in the model info
                // Let's try to extract it
                Object modelfile = response.get("modelfile");
                if (modelfile instanceof String) {
                    String modelfileStr = (String) modelfile;
                    // Look for PARAMETER context_length in the modelfile
                    String contextLengthStr = extractParameter(modelfileStr, "context_length");
                    if (contextLengthStr != null) {
                        try {
                            this.contextWindowTokens = Integer.parseInt(contextLengthStr);
                            log.info("Retrieved context window tokens for model {}: {}", preferredChatModel, this.contextWindowTokens);
                            return;
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse context_length: {}", contextLengthStr);
                        }
                    }
                }

                // If not found in modelfile, check if there's a model_info map
                if (response.containsKey("model_info")) {
                    Object modelInfo = response.get("model_info");
                    if (modelInfo instanceof Map) {
                        Map<String, Object> modelInfoMap = (Map<String, Object>) modelInfo;
                        if (modelInfoMap.containsKey("llama.context_length")) {
                            Object contextLength = modelInfoMap.get("llama.context_length");
                            if (contextLength instanceof Number) {
                                this.contextWindowTokens = ((Number) contextLength).intValue();
                                log.info("Retrieved context window tokens for model {}: {}", preferredChatModel, this.contextWindowTokens);
                                return;
                            }
                        }
                    }
                }
            }

            log.warn("context_length not found in Ollama show response for {}", preferredChatModel);
            setDefaultContextTokens();
        } catch (Exception e) {
            log.error("Failed to retrieve model info from Ollama for {}", preferredChatModel, e);
            setDefaultContextTokens();
        }
    }

    private String extractParameter(String modelfile, String param) {
        String pattern = "PARAMETER " + param + " ";
        int index = modelfile.indexOf(pattern);
        if (index != -1) {
            int start = index + pattern.length();
            int end = modelfile.indexOf('\n', start);
            if (end == -1) end = modelfile.length();
            return modelfile.substring(start, end).trim();
        }
        return null;
    }

    private void setDefaultContextTokens() {
        this.contextWindowTokens = 4096; // Default fallback
        log.info("Using default context window tokens: {}", this.contextWindowTokens);
    }

    public int getContextWindowTokens() {
        return contextWindowTokens;
    }
}