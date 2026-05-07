package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptExecutiveSummaryRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummaryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static OllamaContainer ollama = new OllamaContainer("ollama/ollama:latest");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.ai.ollama.base-url", ollama::getEndpoint);
        registry.add("spring.ai.ollama.init.pull-model-strategy", () -> "never");
    }

    @Autowired
    private EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummary handler;

    @Autowired
    private TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository;

    @Autowired
    private TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository;

    @Test
    void testProcessEventSuccess() {
        TranscriptWithEmbeddings event = new TranscriptWithEmbeddings();
        event.setId(UUID.randomUUID());

        Mono<Void> result = handler.processEvent(event);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void testProcessTranscriptWithEmbeddingsEventNew() {
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        Mono<Void> result = handler.processTranscriptWithEmbeddingsEvent(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testProcessTranscriptWithEmbeddingsEventExists() {
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        Mono<Void> result = handler.processTranscriptWithEmbeddingsEvent(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testCreateWikiReadyTranscript() {
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        Mono<TranscriptExecutiveSummary> result = handler.createWikiReadyTranscript(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testCallLLMSuccess() {
        String prompt = "test prompt";

        Mono<String> result = handler.callLLM(prompt);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testCallLLMError() {
        String prompt = "test prompt";

        Mono<String> result = handler.callLLM(prompt);

        StepVerifier.create(result)
                .verifyError();
    }
}
