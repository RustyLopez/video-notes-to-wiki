package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscriptStatus;
import com.chaostensor.video_notes_to_wiki.repository.SimplifiedTranscriptRepository;
import com.chaostensor.video_notes_to_wiki.service.SimplifiedTranscriptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/simplified-transcripts")
public class SimplifiedTranscriptsController {

    private final SimplifiedTranscriptRepository simplifiedTranscriptRepository;
    private final SimplifiedTranscriptService simplifiedTranscriptService;

    public SimplifiedTranscriptsController(SimplifiedTranscriptRepository simplifiedTranscriptRepository,
                                           SimplifiedTranscriptService simplifiedTranscriptService) {
        this.simplifiedTranscriptRepository = simplifiedTranscriptRepository;
        this.simplifiedTranscriptService = simplifiedTranscriptService;
    }

    @PostMapping
    public Mono<ResponseEntity<SimplifiedTranscriptResponse>> createSimplifiedTranscript(@RequestBody SimplifiedTranscriptRequest request) {
        SimplifiedTranscript simplifiedTranscript = new SimplifiedTranscript();
        simplifiedTranscript.setId(UUID.randomUUID());
        simplifiedTranscript.setJobId(request.getJobId());
        simplifiedTranscript.setTranscriptSubId(request.getTranscriptSubId());
        simplifiedTranscript.setStatus(SimplifiedTranscriptStatus.PENDING);
        simplifiedTranscript.setCreatedAt(LocalDateTime.now());
        simplifiedTranscript.setUpdatedAt(LocalDateTime.now());

        return simplifiedTranscriptRepository.save(simplifiedTranscript)
            .doOnNext(saved -> {
                // Start async processing
                simplifiedTranscriptService.processSimplifiedTranscript(saved.getId()).subscribe();
            })
            .map(saved -> ResponseEntity.ok(new SimplifiedTranscriptResponse(saved.getId())));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<SimplifiedTranscript>> getSimplifiedTranscript(@PathVariable UUID id) {
        return simplifiedTranscriptRepository.findById(id)
            .map(simplifiedTranscript -> ResponseEntity.ok(simplifiedTranscript))
            .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }
}