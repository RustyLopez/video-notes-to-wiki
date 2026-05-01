package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.service.TranscriptService;
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
    private final TranscriptService transcriptService;

    public TranscriptRawController(TranscriptRepository transcriptRepository,
                                     TranscriptService transcriptService) {
        this.transcriptRepository = transcriptRepository;
        this.transcriptService = transcriptService;
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