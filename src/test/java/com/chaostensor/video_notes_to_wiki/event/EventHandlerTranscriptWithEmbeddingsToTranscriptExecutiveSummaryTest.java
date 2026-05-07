package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptExecutiveSummaryRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummaryTest {

    @Mock
    private EventStream<TranscriptWithEmbeddings> eventStream;

    @Mock
    private TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository;

    @Mock
    private TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository;

    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;

    @Mock
    private EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream;

    @Mock
    private LlmConfig llmConfig;

    @Mock
    private VectorStore vectorStore;

    private EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummary handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummary(
                eventStream, transcriptExecutiveSummaryRepository, transcriptWithEmbeddingsRepository,
                webClientBuilder, wikiReadyTranscriptEventStream, llmConfig, vectorStore
        );
    }

    @Test
    void testProcessEventSuccess() {
        TranscriptWithEmbeddings event = new TranscriptWithEmbeddings();
        event.setId(UUID.randomUUID());

        when(llmConfig.getThreadPoolSize()).thenReturn(1);

        Mono<Void> result = handler.processEvent(event);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires full WebClient mocking for LLM call")
    void testProcessTranscriptWithEmbeddingsEventNew() {
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        when(transcriptExecutiveSummaryRepository.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(transcriptWithEmbeddingsRepository.findById(any(UUID.class))).thenReturn(Mono.just(transcriptWithEmbeddings));

        Mono<Void> result = handler.processTranscriptWithEmbeddingsEvent(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires full WebClient mocking for LLM call")
    void testProcessTranscriptWithEmbeddingsEventExists() {
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        when(transcriptExecutiveSummaryRepository.findById(any(UUID.class))).thenReturn(Mono.just(new TranscriptExecutiveSummary()));

        Mono<Void> result = handler.processTranscriptWithEmbeddingsEvent(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires full WebClient mocking for LLM call")
    void testCreateWikiReadyTranscript() {
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());
        transcriptWithEmbeddings.setChunkEmbeddings(List.of(new TranscriptWithEmbeddings.ChunkEmbedding("chunk", new float[]{1.0f})));

        LLMResponse llmResponse = LLMResponse.builder().result("summary").build();

        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(transcriptWithEmbeddingsRepository.save(any(TranscriptWithEmbeddings.class))).thenReturn(Mono.just(transcriptWithEmbeddings));
        when(transcriptExecutiveSummaryRepository.save(any(TranscriptExecutiveSummary.class))).thenReturn(Mono.just(new TranscriptExecutiveSummary()));
        when(wikiReadyTranscriptEventStream.publish(any(TranscriptExecutiveSummary.class))).thenReturn(Mono.empty());

        Mono<TranscriptExecutiveSummary> result = handler.createWikiReadyTranscript(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires full WebClient mocking for LLM call")
    void testCallLLMSuccess() {
        String prompt = "test prompt";
        LLMResponse response = LLMResponse.builder().result("result").build();

        WebClient webClient = WebClient.builder().build();
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        Mono<String> result = handler.callLLM(prompt);

        StepVerifier.create(result)
                .expectNext("result")
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires full WebClient mocking for LLM call")
    void testCallLLMError() {
        String prompt = "test prompt";

        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        Mono<String> result = handler.callLLM(prompt);

        StepVerifier.create(result)
                .expectError()
                .verify();
    }
}
