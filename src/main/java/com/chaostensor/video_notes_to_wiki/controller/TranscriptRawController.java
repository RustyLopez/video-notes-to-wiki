package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.service.TranscriptionService;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.event.EventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/transcribe")
public class TranscriptRawController {

    private final TranscriptRepository transcriptRepository;
    private final TranscriptionService transcriptionService;
    private final EventPublisher<TranscriptRaw> eventPublisher;

    public TranscriptRawController(TranscriptRepository transcriptRepository,
                                   TranscriptionService transcriptionService,
                                   EventPublisher<TranscriptRaw> eventPublisher) {
        this.transcriptRepository = transcriptRepository;
        this.transcriptionService = transcriptionService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public Mono<ResponseEntity<DtoTranscriptRaw>> create(@RequestBody DtoTranscriptRaw request) {
        TranscriptRaw transcriptRaw = new TranscriptRaw();
        transcriptRaw.setId(UUID.randomUUID());
        transcriptRaw.setStatus(LlmStatus.PENDING);
        transcriptRaw.setVideoPath(request.getVideoPath());

        return transcriptRepository.save(transcriptRaw)
            .doOnNext(savedTranscript -> {
                // Start async processing
                transcriptionService.processTranscript(savedTranscript)
                    .flatMap(completedTranscript -> {
                        if (completedTranscript.getStatus() == LlmStatus.COMPLETED) {
                            // Publish event for completed transcript
                            return eventPublisher.publish(completedTranscript);
                        }
                        return Mono.empty();
                    })
                    .subscribe();
            })
            .flatMap(savedTranscript -> {
                // For immediate response, we return the ID. The actual transcript will be available later
                return Mono.just(ResponseEntity.accepted().body(
                        request.toBuilder()
                                .id(savedTranscript.getId())
                                .status(LlmStatus.PENDING)
                                .videoPath(request.getVideoPath())
                                .build()
                ));
            });
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<DtoTranscriptRaw>> get(@PathVariable UUID id) {
        return transcriptRepository.findById(id)
            .<ResponseEntity<DtoTranscriptRaw>>flatMap(transcript -> {
                if (transcript.getStatus() == LlmStatus.COMPLETED) {
                    DtoTranscriptRaw response =
                            DtoTranscriptRaw.builder()
                                    .id(transcript.getId())
                                    .status(LlmStatus.PENDING)
                                    .videoPath(transcript.getVideoPath())
                                    .build();
                    return Mono.just(ResponseEntity.ok(response));
                } else if (transcript.getStatus() == LlmStatus.PROCESSING) {
                    DtoTranscriptRaw response =
                            DtoTranscriptRaw.builder()
                                    .id(transcript.getId())
                                    .status(LlmStatus.PROCESSING)
                                    .videoPath(transcript.getVideoPath())
                                    .build();
                    return Mono.just(ResponseEntity.accepted().body(response));
                } else {
                    return Mono.just(ResponseEntity.<DtoTranscriptRaw>badRequest().build());
                }
            })
            .defaultIfEmpty(ResponseEntity.<DtoTranscriptRaw>notFound().build());
    }
}