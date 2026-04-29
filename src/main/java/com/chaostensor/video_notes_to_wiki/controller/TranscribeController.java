package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.service.TranscriptionService;
import com.chaostensor.video_notes_to_wiki.entity.Transcript;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptStatus;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.event.EventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/transcribe")
public class TranscribeController {

    private final TranscriptRepository transcriptRepository;
    private final TranscriptionService transcriptionService;
    private final EventPublisher<Transcript> eventPublisher;

    public TranscribeController(TranscriptRepository transcriptRepository,
                               TranscriptionService transcriptionService,
                               EventPublisher<Transcript> eventPublisher) {
        this.transcriptRepository = transcriptRepository;
        this.transcriptionService = transcriptionService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public Mono<ResponseEntity<TranscribeResponse>> transcribeVideo(@RequestBody TranscribeRequest request) {
        Transcript transcript = new Transcript();
        transcript.setId(UUID.randomUUID());
        transcript.setStatus(TranscriptStatus.PENDING);
        transcript.setVideoPath(request.getVideoPath());

        return transcriptRepository.save(transcript)
            .doOnNext(savedTranscript -> {
                // Start async processing
                transcriptionService.processTranscript(savedTranscript)
                    .flatMap(completedTranscript -> {
                        if (completedTranscript.getStatus() == TranscriptStatus.COMPLETED) {
                            // Publish event for completed transcript
                            return eventPublisher.publish(completedTranscript);
                        }
                        return Mono.empty();
                    })
                    .subscribe();
            })
            .flatMap(savedTranscript -> {
                // For immediate response, we return the ID. The actual transcript will be available later
                return Mono.just(ResponseEntity.accepted().body(new TranscribeResponse(savedTranscript.getId(), null)));
            });
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<TranscribeResponse>> getTranscript(@PathVariable UUID id) {
        return transcriptRepository.findById(id)
            .<ResponseEntity<TranscribeResponse>>flatMap(transcript -> {
                if (transcript.getStatus() == TranscriptStatus.COMPLETED) {
                    TranscribeResponse response = new TranscribeResponse(transcript.getId(), transcript.getTranscript());
                    return Mono.just(ResponseEntity.ok(response));
                } else if (transcript.getStatus() == TranscriptStatus.PROCESSING) {
                    TranscribeResponse response = new TranscribeResponse(transcript.getId(), null);
                    return Mono.just(ResponseEntity.accepted().body(response));
                } else {
                    return Mono.just(ResponseEntity.<TranscribeResponse>badRequest().build());
                }
            })
            .defaultIfEmpty(ResponseEntity.<TranscribeResponse>notFound().build());
    }
}