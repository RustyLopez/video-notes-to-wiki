package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import com.chaostensor.video_notes_to_wiki.entity.Transcript;
import com.chaostensor.video_notes_to_wiki.entity.WikiReadyTranscript;
import com.chaostensor.video_notes_to_wiki.entity.WikiResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventConfig {

    @Bean
    public EventPublisher<Transcript> transcriptEventPublisher() {
        return new InMemoryEventPublisher<>();
    }

    @Bean
    public EventPublisher<SimplifiedTranscript> simplifiedTranscriptEventPublisher() {
        return new InMemoryEventPublisher<>();
    }

    @Bean
    public EventPublisher<WikiReadyTranscript> wikiReadyTranscriptEventPublisher() {
        return new InMemoryEventPublisher<>();
    }

    @Bean
    public EventPublisher<WikiResult> wikiResultEventPublisher() {
        return new InMemoryEventPublisher<>();
    }
}