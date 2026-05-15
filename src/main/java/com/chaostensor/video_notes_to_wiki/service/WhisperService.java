package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.WhisperRequest;
import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.WhisperResponse;
import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.CompletedStatus;
import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.FailedStatus;
import com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient.PendingStatus;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(WhisperService.class);

    private final WebClient webClient;

    public WhisperService(final WebClient.Builder builder, @Value("${app.whisper-service-url}") final String whisperServiceUrl) {
        // TODO allow the runner to be configurable as either insanley-fast-whisper, or whisper-x or speaches
        //   with that latter being currently in its own container but the former two currently sharing a container
        //   but at different endpoints.
        this.webClient = builder.baseUrl(whisperServiceUrl).build();
    }

    public Mono<String> transcribeVideo(final String filePath) {
        // TODO we  have some implementation coupling here perhaps
        //   the service expects a file name relative to its mounted volume
        //  which is fine, but this method is accepting a path, not a name..
        // and so is assuming that its file path is identical where in reality
        // this service could be mounting the files to a different path within the container or host environment.
        // going to leave this for now but we need to clean it up eventually
        final String fileName = java.nio.file.Paths.get(filePath).getFileName().toString();
        return transcribeSingle(fileName);
    }

    private Mono<String> transcribeSingle(final String fileName) {
        return webClient.post()
            .uri("")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                    WhisperRequest.builder().fileName(fileName).build()
            )
            .retrieve()
            .bodyToMono(WhisperResponse.class)
            .flatMap(response -> {
                final String jobId = response.getJobId();
                return pollForResult(jobId);
            });
    }

    private Mono<String> pollForResult(final String jobId) {
        return webClient.get()
            .uri("/{jobId}", jobId)
            .retrieve()
            .bodyToMono(WhisperResponse.class)
            .flatMap(response -> {
                if (response.getStatus() instanceof CompletedStatus(String transcriptData)) {
                    return Mono.just(transcriptData);
                }
                if (response.getStatus() instanceof FailedStatus) {
                    return Mono.error(new RuntimeException("Transcription failed for job " + jobId));
                }
                return Mono.error(new RuntimeException("Unknown status for job " + jobId));

            })
            .timeout(java.time.Duration.ofMinutes(30)) // timeout after 30 minutes TODO: make this configurable or.. even discoverable .. or inferrable.  It can be pretty long...
                // k we need a much longer backoff here if we are going to actually wait 30 minutes.
                // TODO these two configurations are kind of interdependent, maybe derive the timeout from teh backoff or vice versa.
            .retryWhen(Retry.backoff(30, java.time.Duration.ofSeconds(60))
                .doOnRetry(signal -> logger.warn("Retrying poll for job {} after failure: {}", jobId, signal.failure().getMessage()))); // retry on error
    }
}