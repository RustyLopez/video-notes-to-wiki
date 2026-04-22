package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.WikiReadyTranscriptCompletionGroup;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
public class WikiReadyTranscriptCompletionGroupEventPublisher implements EventPublisher<WikiReadyTranscriptCompletionGroup> {

    private final Sinks.Many<WikiReadyTranscriptCompletionGroup> sink = Sinks.many().multicast().onBackpressureBuffer();

    public Flux<WikiReadyTranscriptCompletionGroup> getEventStream() {
        return sink.asFlux();
    }

    @Override
    public Mono<Void> publish(WikiReadyTranscriptCompletionGroup event) {
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                // Handle emission failure, perhaps log or throw
                throw new RuntimeException("Failed to emit event: " + result);
            }
        });
    }
}