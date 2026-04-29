package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.service.WhisperService;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.event.EventStream;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/transcripts")
public class TranscriptRawController {

    private final TranscriptRepository transcriptRepository;
    private final WhisperService whisperService;
    private final EventStream<TranscriptRaw> eventStream;

    public TranscriptRawController(TranscriptRepository transcriptRepository,
                                    WhisperService whisperService,
                                    EventStream<TranscriptRaw> eventStream) {
        this.transcriptRepository = transcriptRepository;
        this.whisperService = whisperService;
        this.eventStream = eventStream;
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
                processTranscript(savedTranscript)
                    .flatMap(completedTranscript -> {
                        if (completedTranscript.getStatus() == LlmStatus.COMPLETED) {
                            // Publish event for completed transcript
                            return eventStream.publish(completedTranscript);
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

    private Mono<TranscriptRaw> processTranscript(TranscriptRaw transcriptRaw) {
        transcriptRaw.setStatus(LlmStatus.PROCESSING);
        return transcriptRepository.save(transcriptRaw)
            .flatMap(savedTranscript -> {
                return whisperService.transcribeVideo(savedTranscript.getVideoPath())
                    .flatMap(transcriptText -> {
                        savedTranscript.setTranscript(transcriptText);
                        savedTranscript.setStatus(LlmStatus.COMPLETED);
                        return transcriptRepository.save(savedTranscript);
                    })
                    .onErrorResume(e -> {
                        savedTranscript.setStatus(LlmStatus.FAILED);
                        return transcriptRepository.save(savedTranscript);
                    });
            });
    }
}