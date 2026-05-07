package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

class KnowledgeBaseControllerTest {

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private WebClient.Builder webClientBuilder;

    @MockitoBean
    private LlmConfig llmConfig;

    @Test
    void query_shouldReturnBadRequestForEmptyBody() {
        WebTestClient.bindToController(new KnowledgeBaseController(vectorStore, webClientBuilder, llmConfig))
                .build()
                .post()
                .uri("/api/knowledge-base/query")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void answer_shouldReturnBadRequestForEmptyBody() {
        WebTestClient.bindToController(new KnowledgeBaseController(vectorStore, webClientBuilder, llmConfig))
                .build()
                .post()
                .uri("/api/knowledge-base/answer")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
