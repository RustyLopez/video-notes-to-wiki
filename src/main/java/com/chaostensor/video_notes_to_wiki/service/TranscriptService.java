package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.event.EventStream;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


import org.apache.commons.codec.digest.DigestUtils;

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
        // TODO should set to null but we need to either create a patch ddl entry or drop the table and corresponding Liquibase record and patch it.
        transcriptRaw.setTranscriptRaw("");

        return transcriptRepository.save(transcriptRaw)
            .onErrorResume(throwable -> {
                if ("org.springframework.dao.DuplicateKeyException".equals(throwable.getClass().getName()) ||
                    "io.r2dbc.postgresql.ExceptionFactory$PostgresqlDataIntegrityViolationException".equals(throwable.getClass().getName())) {
                    return transcriptRepository.findByHash(hash)
                        .flatMap(existing -> {
                            if (existing.getStatus() == LlmStatus.FAILED) {
                                existing.setStatus(LlmStatus.PROCESSING);
                                return transcriptRepository.save(existing);
                            } else {
                                return Mono.<TranscriptRaw>error(new IllegalStateException("Duplicate transcript exists with status " + existing.getStatus()));
                            }
                        });
                } else {
                    return Mono.<TranscriptRaw>error(throwable);
                }
            })
            .doOnNext(savedTranscript -> {
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
        // note seems like a redundant save but we are changing status to processing.
        // probably not necessary but I'm going to leave it for now.
        return transcriptRepository.save(transcriptRaw).flatMap(savedTranscript ->
                whisperService.transcribeVideo(savedTranscript.getVideoPath()).flatMap(transcriptText -> {
                    savedTranscript.setTranscriptRaw(transcriptText);
                    savedTranscript.setStatus(LlmStatus.COMPLETED);
                    return transcriptRepository.save(savedTranscript);
                }).onErrorResume(e -> {
                    savedTranscript.setStatus(LlmStatus.FAILED);
                    return transcriptRepository.save(savedTranscript);
                }));
    }

    private String computeFileHash(final String filePath) throws IOException {
        final Path path = Paths.get(filePath);
        try (final InputStream inputStream = Files.newInputStream(path)) {
            return DigestUtils.sha256Hex(inputStream);
        }
    }
}