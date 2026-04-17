package com.chaostensor.video_notes_to_wiki.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WhisperService {

    private final WebClient webClient;

    public WhisperService(WebClient.Builder builder) {
        // Assume Whisper API is running at http://localhost:8081/whisper
        this.webClient = builder.baseUrl("http://localhost:8081/whisper").build();
    }

    public Mono<Map<String, String>> transcribeVideos(Map<String, String> filePaths) {
        // For each file, call transcribe and collect results
        return Mono.zip(
            filePaths.entrySet().stream()
                .map(entry -> transcribeSingle(entry.getKey(), entry.getValue())
                    .map(transcript -> Map.entry(entry.getKey(), transcript)))
                .collect(Collectors.toList()),
            results -> results.stream()
                .map(obj -> (Map.Entry<String, String>) obj)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }

    private Mono<String> transcribeSingle(String fileName, String filePath) {
        return webClient.post()
            .uri("/transcribe")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("file", new FileSystemResource(filePath)))
            .retrieve()
            .bodyToMono(String.class)
            .onErrorResume(e -> Mono.just("Error transcribing " + fileName + ": " + e.getMessage()));
    }
}