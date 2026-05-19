package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.dto.TranscriptCreationResult;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.event.EventStream;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


import org.apache.commons.codec.digest.DigestUtils;

@Service
@Slf4j
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

    public TranscriptCreationResult createTranscript(final String videoPath) {
        final String hash;
        try {
            hash = computeFileHash(videoPath);
        } catch (final Exception e) {
            logger.error("Failed to compute hash for video: {}", videoPath, e);
            return TranscriptCreationResult.builder()
                .initiation(Mono.error(e))
                .completion(Mono.error(e))
                .build();
        }
        return createNewTranscript(videoPath, hash);
    }

    private TranscriptCreationResult createNewTranscript(final String videoPath, final String hash) {
        final TranscriptRaw transcriptRaw = new TranscriptRaw();
        transcriptRaw.setStatus(LlmStatus.PENDING);
        transcriptRaw.setVideoPath(videoPath);
        transcriptRaw.setHash(hash);
        // TODO should set to null but we need to either create a patch ddl entry or drop the table and corresponding Liquibase record and patch it.
        transcriptRaw.setTranscriptRaw("");

        final Mono<TranscriptRaw> saved = transcriptRepository.save(transcriptRaw)
            .onErrorResume(throwable -> {
                if ("org.springframework.dao.DuplicateKeyException".equals(throwable.getClass().getName()) ||
                    "io.r2dbc.postgresql.ExceptionFactory$PostgresqlDataIntegrityViolationException".equals(throwable.getClass().getName())) {
                    logger.warn("Duplicate key conflict for hash {}, attempting recovery", hash, throwable);
                    return transcriptRepository.findByHash(hash)
                        .flatMap(existing -> {
                            if (existing.getStatus() == LlmStatus.FAILED) {
                                existing.setStatus(LlmStatus.PROCESSING);
                                return transcriptRepository.save(existing);
                            } else if (existing.getStatus() == LlmStatus.COMPLETED) {
                                return Mono.just(existing);
                            } else {
                                return Mono.<TranscriptRaw>error(new IllegalStateException("Duplicate transcript exists with status " + existing.getStatus()));
                            }
                        });
                } else {
                    return Mono.<TranscriptRaw>error(throwable);
                }
            });

        // TODO This is all super confusing
         // do this better.
         // we are basically not re-processing if we found an existing record.
         // but the way this is organized, it is like inverted.

        final Mono<TranscriptRaw> initiation = saved.doOnNext(savedTranscript -> {
            if (savedTranscript.getStatus() == LlmStatus.COMPLETED) {
                eventStream.publish(savedTranscript).subscribe(v -> {}, error -> logger.error("Error publishing transcript", error));
            }
        }).share();
        final Mono<TranscriptRaw> completion = initiation.flatMap(savedTranscript -> {
            if (savedTranscript.getStatus() == LlmStatus.COMPLETED) {
                return eventStream.publish(savedTranscript).thenReturn(savedTranscript);
            } else {
                return processTranscript(savedTranscript).flatMap(completedTranscript -> {
                    if (completedTranscript.getStatus() == LlmStatus.COMPLETED) {
                        return eventStream.publish(completedTranscript).thenReturn(completedTranscript);
                    }
                    return Mono.just(completedTranscript);
                });
            }
        }).share();
        completion.subscribe(v -> {}, error -> logger.error("Error processing transcript", error));
        return TranscriptCreationResult.builder().initiation(initiation).completion(completion).build();
    }

    private Mono<TranscriptRaw> processTranscript(final TranscriptRaw transcriptRaw) {
        transcriptRaw.setStatus(LlmStatus.PROCESSING);
        // note seems like a redundant save but we are changing status to processing.
        // probably not necessary but I'm going to leave it for now.
        // NOTE: we have retries that will fail if we are currently processing which is by design.
           // Um the retries wouldn't typically hit again on the same session , but the hashing on the whisper-wrapper
           // side is taking too long to get the initial response back ( before async processing)
           ///  so that increases he likelihood of  the scheduled retry job hitting his method again before
        // we get our initial http response back and move the video out of the idr.
        return transcriptRepository.save(transcriptRaw).flatMap(savedTranscript ->
                whisperService.transcribeVideo(savedTranscript.getVideoPath()).flatMap(transcriptText -> {
                    savedTranscript.setTranscriptRaw(transcriptText);
                    savedTranscript.setStatus(LlmStatus.COMPLETED);
                    return transcriptRepository.save(savedTranscript);
                }).onErrorResume(e -> {
                    logger.error("Error processing transcript", e);
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