package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.service.TranscriptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class TranscriptRawControllerTest {

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

    @MockitoBean
    private TranscriptRepository transcriptRepository;

    @MockitoBean
    private TranscriptService transcriptService;

    @Test
    void get_shouldReturnOkWhenCompleted() {
        UUID id = UUID.randomUUID();
        TranscriptRaw transcript = new TranscriptRaw();
        transcript.setId(id);
        transcript.setStatus(LlmStatus.COMPLETED);
        transcript.setVideoPath("/path/to/video.mp4");

        when(transcriptRepository.findById(id)).thenReturn(Mono.just(transcript));

        WebTestClient.bindToController(new TranscriptRawController(transcriptRepository, transcriptService))
                .build()
                .get()
                .uri("/transcripts/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString());
    }

    @Test
    void get_shouldReturnAcceptedWhenProcessing() {
        UUID id = UUID.randomUUID();
        TranscriptRaw transcript = new TranscriptRaw();
        transcript.setId(id);
        transcript.setStatus(LlmStatus.PROCESSING);
        transcript.setVideoPath("/path/to/video.mp4");

        when(transcriptRepository.findById(id)).thenReturn(Mono.just(transcript));

        WebTestClient.bindToController(new TranscriptRawController(transcriptRepository, transcriptService))
                .build()
                .get()
                .uri("/transcripts/{id}", id)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString());
    }

    @Test
    void get_shouldReturnNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(transcriptRepository.findById(id)).thenReturn(Mono.empty());

        WebTestClient.bindToController(new TranscriptRawController(transcriptRepository, transcriptService))
                .build()
                .get()
                .uri("/transcripts/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }
}
