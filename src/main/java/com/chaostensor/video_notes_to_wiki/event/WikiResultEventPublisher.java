package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.WikiResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
public class WikiResultEventPublisher implements EventPublisher<WikiResult> {

    private final Sinks.Many<WikiResult> sink = Sinks.many().multicast().onBackpressureBuffer();

    public Flux<WikiResult> getEventStream() {
        return sink.asFlux();
    }

    @Override
    public Mono<Void> publish(WikiResult event) {
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                // Handle emission failure, perhaps log or throw
                throw new RuntimeException("Failed to emit event: " + result);
            }
        });
    }
}