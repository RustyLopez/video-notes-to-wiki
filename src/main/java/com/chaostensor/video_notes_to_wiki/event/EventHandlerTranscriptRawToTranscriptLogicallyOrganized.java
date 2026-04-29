package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptLogicallyOrganized;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptLogicallyOrganizedRepository;
import com.chaostensor.video_notes_to_wiki.service.SimplifiedTranscriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import jakarta.annotation.PostConstruct;

@Component
public class EventHandlerTranscriptRawToTranscriptLogicallyOrganized implements EventHandler<TranscriptRaw> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerTranscriptRawToTranscriptLogicallyOrganized.class);

    private final EventPublisher<TranscriptRaw> transcriptEventPublisher;
    private final TranscriptLogicallyOrganizedRepository transcriptLogicallyOrganizedRepository;
    private final SimplifiedTranscriptService simplifiedTranscriptService;
    private Disposable subscription;

    public EventHandlerTranscriptRawToTranscriptLogicallyOrganized(EventPublisher<TranscriptRaw> transcriptEventPublisher,
                                                                   TranscriptLogicallyOrganizedRepository transcriptLogicallyOrganizedRepository,
                                                                   SimplifiedTranscriptService simplifiedTranscriptService) {
        this.transcriptEventPublisher = transcriptEventPublisher;
        this.transcriptLogicallyOrganizedRepository = transcriptLogicallyOrganizedRepository;
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

    private reactor.core.publisher.Mono<Void> processTranscriptEvent(TranscriptRaw transcriptRaw) {
        // Create a simplified transcript for this completed transcript
        TranscriptLogicallyOrganized transcriptLogicallyOrganized = new TranscriptLogicallyOrganized();
        transcriptLogicallyOrganized.setId(java.util.UUID.randomUUID());
        transcriptLogicallyOrganized.setTransccriptRawId(transcriptRaw.getId());
        transcriptLogicallyOrganized.setStatus(LlmStatus.PENDING);
        transcriptLogicallyOrganized.setCreatedAt(java.time.LocalDateTime.now());
        transcriptLogicallyOrganized.setUpdatedAt(java.time.LocalDateTime.now());

        return transcriptLogicallyOrganizedRepository.save(transcriptLogicallyOrganized)
            .doOnNext(saved -> {
                // Start async processing to create simplified version
                simplifiedTranscriptService.processSimplifiedTranscript(saved.getId()).subscribe();
            })
            .then();
    }
}