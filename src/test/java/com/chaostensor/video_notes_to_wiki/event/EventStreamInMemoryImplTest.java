package com.chaostensor.video_notes_to_wiki.event;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.TimeUnit;

class EventStreamInMemoryImplTest {

    @Test
    void testPublishAndGetEventStream() {
        EventStream<String> eventStream = new EventStreamInMemoryImpl<>();

        Flux<String> flux = eventStream.getEventStream();

        StepVerifier.create(flux.take(2))
                .then(() -> eventStream.publish("event1").subscribe())
                .expectNext("event1")
                .then(() -> eventStream.publish("event2").subscribe())
                .expectNext("event2")
                .thenCancel()
                .verify();
    }

    @Test
    void testPublishFailure() {
        EventStream<String> eventStream = new EventStreamInMemoryImpl<>();

        // Publishing should succeed normally
        Mono<Void> result = eventStream.publish("event");

        StepVerifier.create(result)
                .verifyComplete();
    }
}