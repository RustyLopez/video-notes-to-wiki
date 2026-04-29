package com.chaostensor.video_notes_to_wiki.event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventStream<T> {
    Mono<Void> publish(T event);
    Flux<T> getEventStream();
}