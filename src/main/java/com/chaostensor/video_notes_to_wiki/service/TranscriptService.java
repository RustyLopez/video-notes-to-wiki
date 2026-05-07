package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.event.EventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
public class TranscriptService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptService.class);

    private final TranscriptRepository transcriptRepository;
    private final WhisperService whisperService;
    private final EventStream<TranscriptRaw> eventStream;

    public TranscriptService(final TranscriptRepository transcriptRepository, final WhisperService whisperService, final EventStream<TranscriptRaw> eventStream) {
        this.transcriptRepository = transcriptRepository;
        this.whisperService = whisperService;
        this.eventStream = eventStream;
    }

    public Mono<TranscriptRaw> createTranscript(final String videoPath) {
        final String hash;
        try {
            hash = computeFileHash(videoPath);
        } catch (final Exception e) {
            logger.error("Failed to compute hash for video: {}", videoPath, e);
            return Mono.error(e);
        }

        return transcriptRepository.findByVideoPathAndHash(videoPath, hash).flatMap(existing -> {
            logger.warn("Video at path {} with hash {} is a duplicate. Not transcribing. If this is not a duplicate, please rename the file.", videoPath, hash);
            return Mono.<TranscriptRaw>empty();
        }).switchIfEmpty(transcriptRepository.findByHash(hash).flatMap(existing -> {
            logger.warn("Video with hash {} already exists at different path {}. Proceeding with transcription.", hash, existing.getVideoPath());
            return createNewTranscript(videoPath, hash);
        }).switchIfEmpty(Mono.defer(() -> createNewTranscript(videoPath, hash))));
    }

    private Mono<TranscriptRaw> createNewTranscript(final String videoPath, final String hash) {
        final TranscriptRaw transcriptRaw = new TranscriptRaw();
        transcriptRaw.setStatus(LlmStatus.PENDING);
        transcriptRaw.setVideoPath(videoPath);
        transcriptRaw.setHash(hash);

        return transcriptRepository.save(transcriptRaw).doOnNext(savedTranscript -> {
            // Start async processing
            processTranscript(savedTranscript).flatMap(completedTranscript -> {
                if (completedTranscript.getStatus() == LlmStatus.COMPLETED) {
                    // Publish event for completed transcript
                    return eventStream.publish(completedTranscript);
                }
                return Mono.empty();
            }).subscribe(v -> {
            }, error -> logger.error("Error processing transcript", error));
        });
    }

    private Mono<TranscriptRaw> processTranscript(final TranscriptRaw transcriptRaw) {
        transcriptRaw.setStatus(LlmStatus.PROCESSING);
        return transcriptRepository.save(transcriptRaw).flatMap(savedTranscript -> {
            return whisperService.transcribeVideo(savedTranscript.getVideoPath()).flatMap(transcriptText -> {
                savedTranscript.setTranscript(transcriptText);
                savedTranscript.setStatus(LlmStatus.COMPLETED);
                return transcriptRepository.save(savedTranscript);
            }).onErrorResume(e -> {
                savedTranscript.setStatus(LlmStatus.FAILED);
                return transcriptRepository.save(savedTranscript);
            });
        });
    }

    private String computeFileHash(final String filePath) throws IOException, NoSuchAlgorithmException {
        final Path path = Paths.get(filePath);
        final byte[] fileBytes = Files.readAllBytes(path);
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] hashBytes = digest.digest(fileBytes);
        final StringBuilder sb = new StringBuilder();
        for (final byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}