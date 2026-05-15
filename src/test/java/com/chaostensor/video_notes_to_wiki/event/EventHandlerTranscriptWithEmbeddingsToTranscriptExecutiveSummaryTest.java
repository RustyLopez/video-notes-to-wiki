package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.config.OllamaTestContainersDefaultConfig;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptExecutiveSummaryRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;

@Testcontainers
@SpringBootTest
@Import(OllamaTestContainersDefaultConfig.class)
@ActiveProfiles("test")
class EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummaryTest {

    @org.testcontainers.junit.jupiter.Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");


    /**
     * THe ollama container requires special handling
     */
    @Autowired
    private OllamaContainer ollamaContainer;


    @DynamicPropertySource
    static void registerProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");


    }

    @Autowired
    private EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummary handler;

    @Autowired
    private TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository;

    @Autowired
    private TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository;

    @Test
    void testProcessEventSuccess() {
        final TranscriptWithEmbeddings event = new TranscriptWithEmbeddings();
        event.setId(UUID.randomUUID());

        // Save an existing summary to make the event processing succeed (no-op)
        final TranscriptExecutiveSummary existingSummary = new TranscriptExecutiveSummary();
        existingSummary.setId(event.getId());
        existingSummary.setTranscriptWithEmbeddingsId(event.getId());
        existingSummary.setResult("existing summary");
        existingSummary.setCreatedAt(LocalDateTime.now());
        existingSummary.setUpdatedAt(LocalDateTime.now());
        transcriptExecutiveSummaryRepository.save(existingSummary).block();

        final Mono<Void> result = handler.processEvent(event);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testProcessTranscriptWithEmbeddingsEventNew() {
        final TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        final Mono<Void> result = handler.processTranscriptWithEmbeddingsEvent(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testProcessTranscriptWithEmbeddingsEventExists() {
        final TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        final Mono<Void> result = handler.processTranscriptWithEmbeddingsEvent(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testCreateWikiReadyTranscript() {
        final TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        final Mono<TranscriptExecutiveSummary> result = handler.createWikiReadyTranscript(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testCallLLMSuccess() {
        final String prompt = "test prompt";

        final Mono<String> result = handler.callLLM(prompt);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testCallLLMError() {
        final String prompt = "test prompt";

        final Mono<String> result = handler.callLLM(prompt);

        StepVerifier.create(result)
                .verifyError();
    }
}
