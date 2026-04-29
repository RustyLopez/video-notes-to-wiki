package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptLogicallyOrganized;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.CompressedTranscripts;
import com.chaostensor.video_notes_to_wiki.entity.Wiki;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventConfig {

    @Bean
    public EventPublisher<TranscriptRaw> transcriptEventPublisher() {
        return new InMemoryEventPublisher<>();
    }

    @Bean
    public EventPublisher<TranscriptLogicallyOrganized> simplifiedTranscriptEventPublisher() {
        return new InMemoryEventPublisher<>();
    }

    @Bean
    public EventPublisher<TranscriptExecutiveSummary> wikiReadyTranscriptEventPublisher() {
        return new InMemoryEventPublisher<>();
    }

    @Bean
    public EventPublisher<CompressedTranscripts> compressedTranscriptsEventPublisher() {
        return new InMemoryEventPublisher<>();
    }

    @Bean
    public EventPublisher<Wiki> wikiResultEventPublisher() {
        return new InMemoryEventPublisher<>();
    }
}