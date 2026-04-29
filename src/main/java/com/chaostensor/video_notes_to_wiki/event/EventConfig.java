package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptLogicallyOrganized;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptsHierarchicalRollup;
import com.chaostensor.video_notes_to_wiki.entity.Wiki;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventConfig {

    @Bean
    public EventStream<TranscriptRaw> transcriptEventPublisher() {
        return new EventStreamInMemoryImpl<>();
    }

    @Bean
    public EventStream<TranscriptLogicallyOrganized> simplifiedTranscriptEventPublisher() {
        return new EventStreamInMemoryImpl<>();
    }

    @Bean
    public EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventPublisher() {
        return new EventStreamInMemoryImpl<>();
    }

    @Bean
    public EventStream<TranscriptsHierarchicalRollup> compressedTranscriptsEventPublisher() {
        return new EventStreamInMemoryImpl<>();
    }

    @Bean
    public EventStream<Wiki> wikiResultEventPublisher() {
        return new EventStreamInMemoryImpl<>();
    }
}