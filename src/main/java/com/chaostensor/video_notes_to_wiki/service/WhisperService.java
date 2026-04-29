package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.WhisperRequest;
import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.WhisperResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WhisperService {

    private final WebClient webClient;

    public WhisperService(WebClient.Builder builder) {
        // TODO allow the runner to be configurable as either insanley-fast-whisper, or whisper-x or speaches
        //   with that latter being currently in its own container but the former two currently sharing a container
        //   but at different endpoints.
        this.webClient = builder.baseUrl("http://localhost:8081/insanely-fast-whisper").build();
    }

    public Mono<String> transcribeVideo(String filePath) {
        String fileName = java.nio.file.Paths.get(filePath).getFileName().toString();
        return transcribeSingle(fileName, filePath);
    }

    public Mono<Map<String, String>> transcribeVideos(Map<String, String> filePaths) {
        // For each file, call transcribe and collect results
        return Mono.zip(
            filePaths.entrySet().stream()
                .map(entry -> transcribeSingle(entry.getKey(), entry.getValue())
                    .map(transcript -> Map.entry(entry.getKey(), transcript)))
                .collect(Collectors.toList()),
            results -> Arrays.stream(results)
                .map(obj -> (Map.Entry<String, String>) obj)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }

    private Mono<String> transcribeSingle(String fileName, String filePath) {
        return webClient.post()
            .uri("")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                    WhisperRequest.builder().pathRelativeSharedVolumeMount(filePath).build()
            )
            .retrieve()
            .bodyToMono(WhisperResponse.class)
                .map(WhisperResponse::getJobId)
            .onErrorResume(e -> Mono.just("Error transcribing " + fileName + ": " + e.getMessage()));
    }
}