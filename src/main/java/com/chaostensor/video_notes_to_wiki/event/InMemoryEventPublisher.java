package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
public class InMemoryEventPublisher implements EventPublisher<SimplifiedTranscript> {

    private final Sinks.Many<SimplifiedTranscript> sink = Sinks.many().multicast().onBackpressureBuffer();

    public Flux<SimplifiedTranscript> getEventStream() {
        return sink.asFlux();
    }

    @Override
    public Mono<Void> publish(SimplifiedTranscript event) {
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                // Handle emission failure, perhaps log or throw
                throw new RuntimeException("Failed to emit event: " + result);
            }
        });
    }
}