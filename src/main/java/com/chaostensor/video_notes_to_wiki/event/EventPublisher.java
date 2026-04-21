package com.chaostensor.video_notes_to_wiki.event;

import reactor.core.publisher.Mono;

public interface EventPublisher<T> {
    Mono<Void> publish(T event);
}