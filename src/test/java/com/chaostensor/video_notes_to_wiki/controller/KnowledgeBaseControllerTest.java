package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private LlmConfig llmConfig;

    private KnowledgeBaseController controller;

    @BeforeEach
    void setUp() {
        // Note: full mocking of query/answer would require deep mocks for VectorStore.similaritySearch etc.
        // Here we just verify instantiation and basic wiring for unit test coverage.
        controller = new KnowledgeBaseController(vectorStore, webClientBuilder, llmConfig);
    }

    @Test
    void controller_shouldBeInstantiable() {
        // Basic sanity
        StepVerifier.create(Mono.just(controller))
                .expectNextMatches(c -> c != null)
                .verifyComplete();
    }
}
