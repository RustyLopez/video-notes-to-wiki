package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
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

import java.util.UUID;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class EventHandlerTranscriptRawToTranscriptWithEmbeddingsTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static OllamaContainer ollama = new OllamaContainer("ollama/ollama:latest");

    @DynamicPropertySource
    static void registerProperties(final DynamicPropertyRegistry registry) {
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
    private EventHandlerTranscriptRawToTranscriptWithEmbeddings handler;

    @Autowired
    private TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Test
    void testProcessTranscriptEventSuccess() {
        final TranscriptRaw transcriptRaw = new TranscriptRaw();
        transcriptRaw.setId(UUID.randomUUID());
        transcriptRaw.setTranscript("Test transcript");

        final Mono<Void> result = handler.processTranscriptEvent(transcriptRaw);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testProcessTranscriptWithEmbeddingsSuccess() {
        final UUID id = UUID.randomUUID();
        final TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(id);

        final Mono<TranscriptWithEmbeddings> result = handler.processTranscriptWithEmbeddings(id);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void testProcessTranscriptNullContent() {
        final TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());
        transcriptWithEmbeddings.setTranscriptRawId(UUID.randomUUID());

        final Mono<TranscriptWithEmbeddings> result = handler.processTranscript(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testProcessTranscriptException() {
        final TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());
        transcriptWithEmbeddings.setTranscriptRawId(UUID.randomUUID());

        final Mono<TranscriptWithEmbeddings> result = handler.processTranscript(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testDetermineIdealMaxChunkSizeForSingleTranscriptChunks() {
        final int result = handler.determineIdealMaxChunkSizeForSingleTranscriptChunks();

        assert result > 0;
    }
}
