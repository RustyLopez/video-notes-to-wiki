package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscriptStatus;
import com.chaostensor.video_notes_to_wiki.entity.Transcript;
import com.chaostensor.video_notes_to_wiki.repository.SimplifiedTranscriptRepository;
import com.chaostensor.video_notes_to_wiki.service.SimplifiedTranscriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import jakarta.annotation.PostConstruct;

@Component
public class EventHandlerTranscriptToSimplifiedTranscript implements EventHandler<Transcript> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerTranscriptToSimplifiedTranscript.class);

    private final EventPublisher<Transcript> transcriptEventPublisher;
    private final SimplifiedTranscriptRepository simplifiedTranscriptRepository;
    private final SimplifiedTranscriptService simplifiedTranscriptService;
    private Disposable subscription;

    public EventHandlerTranscriptToSimplifiedTranscript(EventPublisher<Transcript> transcriptEventPublisher,
                                                        SimplifiedTranscriptRepository simplifiedTranscriptRepository,
                                                        SimplifiedTranscriptService simplifiedTranscriptService) {
        this.transcriptEventPublisher = transcriptEventPublisher;
        this.simplifiedTranscriptRepository = simplifiedTranscriptRepository;
        this.simplifiedTranscriptService = simplifiedTranscriptService;
    }

    @PostConstruct
    public void subscribe() {
        subscription = transcriptEventPublisher.getEventStream()
            .flatMap(this::processTranscriptEvent)
            .subscribe(
                null, // onNext
                error -> logger.error("Error in transcript event stream subscription", error),
                () -> logger.info("Transcript event stream completed")
            );
        logger.info("Subscribed to transcript event stream");
    }

    private reactor.core.publisher.Mono<Void> processTranscriptEvent(Transcript transcript) {
        // Create a simplified transcript for this completed transcript
        SimplifiedTranscript simplifiedTranscript = new SimplifiedTranscript();
        simplifiedTranscript.setId(java.util.UUID.randomUUID());
        simplifiedTranscript.setTranscriptId(transcript.getId());
        simplifiedTranscript.setStatus(SimplifiedTranscriptStatus.PENDING);
        simplifiedTranscript.setCreatedAt(java.time.LocalDateTime.now());
        simplifiedTranscript.setUpdatedAt(java.time.LocalDateTime.now());

        return simplifiedTranscriptRepository.save(simplifiedTranscript)
            .doOnNext(saved -> {
                // Start async processing to create simplified version
                simplifiedTranscriptService.processSimplifiedTranscript(saved.getId()).subscribe();
            })
            .then();
    }
}