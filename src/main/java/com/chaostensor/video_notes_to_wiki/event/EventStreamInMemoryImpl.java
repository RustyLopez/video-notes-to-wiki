package com.chaostensor.video_notes_to_wiki.event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class EventStreamInMemoryImpl<T> implements EventStream<T> {

    private final Sinks.Many<T> sink = Sinks.many().multicast().onBackpressureBuffer();

    public Flux<T> getEventStream() {
        return sink.asFlux();
    }

    @Override
    public Mono<Void> publish(final T event) {
        return Mono.fromRunnable(() -> {
            final Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                // Handle emission failure, perhaps log or throw
                throw new RuntimeException("Failed to emit event: " + result);
            }
        });
    }
}