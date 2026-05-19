package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.config.OllamaTestContainersDefaultConfig;
import com.chaostensor.video_notes_to_wiki.dto.ChunkEmbeddingList;
import com.chaostensor.video_notes_to_wiki.entity.ChunkEmbedding;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
@Import(OllamaTestContainersDefaultConfig.class)
@ActiveProfiles("test")
class EventHandlerTranscriptRawToTranscriptWithEmbeddingsTest {

    @Container
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
    private EventHandlerTranscriptRawToTranscriptWithEmbeddings handler;

    @Autowired
    private TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Test
    void testProcessTranscriptEventSuccess() {
        final TranscriptRaw transcriptRaw = new TranscriptRaw();
        transcriptRaw.setId(UUID.randomUUID());
        transcriptRaw.setTranscriptRaw("Test transcript");

        final Mono<Void> result = handler.processTranscriptEvent(transcriptRaw);

        StepVerifier.create(result)
                .verifyError();
    }

    @Test
    void testProcessTranscriptWithEmbeddingsSuccess() {
        final UUID id = UUID.randomUUID();
        ChunkEmbeddingList.of(List.of(new ChunkEmbedding()));
        final Mono<TranscriptWithEmbeddings> result = handler.processTranscriptWithEmbeddings(id);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Disabled// Ww need to support nulls for the initial save, but after that it should fail, work out the right.. strat
    @Test
    void testProcessTranscriptNullContent() {
        final TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());
        transcriptWithEmbeddings.setTranscriptRawId(UUID.randomUUID());

        final Mono<TranscriptWithEmbeddings> result = handler.processTranscript(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyComplete();
    }


    @Test
    void testDetermineIdealMaxChunkSizeForSingleTranscriptChunks() {
        final int result = handler.determineIdealMaxChunkSizeForSingleTranscriptChunks();

        assert result > 0;
    }
}
