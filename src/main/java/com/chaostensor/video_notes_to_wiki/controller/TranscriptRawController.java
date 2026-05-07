package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.service.TranscriptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/transcripts")
public class TranscriptRawController {

    private final TranscriptRepository transcriptRepository;
    private final TranscriptService transcriptService;

    public TranscriptRawController(final TranscriptRepository transcriptRepository,
                                   final TranscriptService transcriptService) {
        this.transcriptRepository = transcriptRepository;
        this.transcriptService = transcriptService;
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<DtoTranscriptRaw>> get(@PathVariable final UUID id) {
        return transcriptRepository.findById(id)
                .<ResponseEntity<DtoTranscriptRaw>>flatMap(transcript -> {
                    if (transcript.getStatus() == LlmStatus.COMPLETED) {
                        final DtoTranscriptRaw response =
                                DtoTranscriptRaw.builder()
                                        .id(transcript.getId())
                                        .status(LlmStatus.PENDING)
                                        .videoPath(transcript.getVideoPath())
                                        .build();
                        return Mono.just(ResponseEntity.ok(response));
                    } else if (transcript.getStatus() == LlmStatus.PROCESSING) {
                        final DtoTranscriptRaw response =
                                DtoTranscriptRaw.builder()
                                        .id(transcript.getId())
                                        .status(LlmStatus.PROCESSING)
                                        .videoPath(transcript.getVideoPath())
                                        .build();
                        return Mono.just(ResponseEntity.accepted().body(response));
                    } else {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }


}