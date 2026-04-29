package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.service.CompressedTranscriptsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

@Component
public class EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsHierarchicalRollup implements EventHandler<TranscriptExecutiveSummary> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsHierarchicalRollup.class);

    private final EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream;
    private final CompressedTranscriptsService compressedTranscriptsService;
    private Disposable subscription;

    public EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsHierarchicalRollup(EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream,
                                                                                                   CompressedTranscriptsService compressedTranscriptsService) {
        this.wikiReadyTranscriptEventStream = wikiReadyTranscriptEventStream;
        this.compressedTranscriptsService = compressedTranscriptsService;
    }

    @PostConstruct
    public void subscribe() {
        subscription = wikiReadyTranscriptEventStream.getEventStream()
            .flatMap(event -> compressedTranscriptsService.processWikiReadyTranscriptEvent(event)
                .doOnError(error -> logger.error("Error processing event for TranscriptExecutiveSummary id: {}", event.getId(), error))
                .onErrorResume(e -> Mono.empty()) // Continue processing other events
            )
            .subscribe(
                null, // onNext
                error -> logger.error("Error in event stream subscription", error),
                () -> logger.info("Event stream completed")
            );
        logger.info("Subscribed to TranscriptExecutiveSummary event stream");
    }

    // Optionally, for shutdown
    // @PreDestroy
    // public void unsubscribe() {
    //     if (subscription != null) {
    //         subscription.dispose();
    //     }
    // }
}