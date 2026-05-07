package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.WhisperRequest;
import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.WhisperResponse;
import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.CompletedStatus;
import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.FailedStatus;
import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.PendingStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WhisperService {

    private final WebClient webClient;

    public WhisperService(final WebClient.Builder builder) {
        // TODO allow the runner to be configurable as either insanley-fast-whisper, or whisper-x or speaches
        //   with that latter being currently in its own container but the former two currently sharing a container
        //   but at different endpoints.
        this.webClient = builder.baseUrl("http://localhost:8081/whispers").build();
    }

    public Mono<String> transcribeVideo(final String filePath) {
        final String fileName = java.nio.file.Paths.get(filePath).getFileName().toString();
        return transcribeSingle(fileName, filePath);
    }

    public Mono<Map<String, String>> transcribeVideos(final Map<String, String> filePaths) {
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

    private Mono<String> transcribeSingle(final String fileName, final String filePath) {
        return webClient.post()
            .uri("")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                    WhisperRequest.builder().fileName(fileName).build()
            )
            .retrieve()
            .bodyToMono(WhisperResponse.class)
            .flatMap(response -> {
                String jobId = response.getJobId();
                return pollForResult(jobId);
            })
            .onErrorResume(e -> Mono.just("Error transcribing " + fileName + ": " + e.getMessage()));
    }

    private Mono<String> pollForResult(String jobId) {
        return webClient.get()
            .uri("/{jobId}", jobId)
            .retrieve()
            .bodyToMono(WhisperResponse.class)
            .flatMap(response -> {
                if (response.getStatus() instanceof CompletedStatus completed) {
                    return Mono.just(completed.transcriptData());
                } else if (response.getStatus() instanceof FailedStatus) {
                    return Mono.error(new RuntimeException("Transcription failed for job " + jobId));
                } else if (response.getStatus() instanceof PendingStatus) {
                    return Mono.delay(java.time.Duration.ofSeconds(5))
                        .then(Mono.defer(() -> pollForResult(jobId)));
                } else {
                    return Mono.error(new RuntimeException("Unknown status for job " + jobId));
                }
            })
            .timeout(java.time.Duration.ofMinutes(30)) // timeout after 30 minutes
            .retryWhen(Retry.backoff(3, java.time.Duration.ofSeconds(5))); // retry on error
    }
}