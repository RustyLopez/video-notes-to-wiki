package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import com.chaostensor.video_notes_to_wiki.service.WikiReadyTranscriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

@Component
public class EventHandlerSimplifiedTranscriptToWikiReadyTranscript implements EventHandler<SimplifiedTranscript> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerCombineLatestAllWikiReadyTranscriptsToWiki.class);

    private final InMemoryEventPublisher<SimplifiedTranscript> eventPublisher;
    private final WikiReadyTranscriptService wikiReadyTranscriptService;
    private Disposable subscription;

    public EventHandlerSimplifiedTranscriptToWikiReadyTranscript(InMemoryEventPublisher<SimplifiedTranscript> eventPublisher,
                                                                 WikiReadyTranscriptService wikiReadyTranscriptService) {
        this.eventPublisher = eventPublisher;
        this.wikiReadyTranscriptService = wikiReadyTranscriptService;
    }

    @PostConstruct
    public void subscribe() {
        subscription = eventPublisher.getEventStream()
            .flatMap(event -> wikiReadyTranscriptService.processSimplifiedTranscriptEvent(event)
                .doOnError(error -> logger.error("Error processing event for SimplifiedTranscript id: {}", event.getId(), error))
                .onErrorResume(e -> Mono.empty()) // Continue processing other events
            )
            .subscribe(
                null, // onNext
                error -> logger.error("Error in event stream subscription", error),
                () -> logger.info("Event stream completed")
            );
        logger.info("Subscribed to SimplifiedTranscript event stream");
    }

    // Optionally, for shutdown
    // @PreDestroy
    // public void unsubscribe() {
    //     if (subscription != null) {
    //         subscription.dispose();
    //     }
    // }
}