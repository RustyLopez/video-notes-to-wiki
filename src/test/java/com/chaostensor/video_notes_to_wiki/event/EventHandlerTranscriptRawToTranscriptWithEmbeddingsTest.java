package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository;
import com.chaostensor.video_notes_to_wiki.service.EmbeddingService;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class EventHandlerTranscriptRawToTranscriptWithEmbeddingsTest {

    @Mock
    private EventStream<TranscriptRaw> transcriptEventStream;

    @Mock
    private TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository;

    @Mock
    private TranscriptRepository transcriptRepository;

    @Mock
    private LlmConfig llmConfig;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EventStream<TranscriptWithEmbeddings> eventStream;

    private EventHandlerTranscriptRawToTranscriptWithEmbeddings handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new EventHandlerTranscriptRawToTranscriptWithEmbeddings(
                transcriptEventStream, transcriptWithEmbeddingsRepository, transcriptRepository,
                llmConfig, embeddingService, vectorStore, eventStream
        );
    }

    @Test
    void testProcessTranscriptEventSuccess() {
        TranscriptRaw transcriptRaw = new TranscriptRaw();
        transcriptRaw.setId(UUID.randomUUID());
        transcriptRaw.setTranscript("Test transcript");

        TranscriptWithEmbeddings savedTranscript = new TranscriptWithEmbeddings();
        savedTranscript.setId(UUID.randomUUID());

        when(transcriptWithEmbeddingsRepository.save(any(TranscriptWithEmbeddings.class)))
                .thenReturn(Mono.just(savedTranscript));
        when(transcriptWithEmbeddingsRepository.findById(any(UUID.class))).thenReturn(Mono.just(savedTranscript));
        when(eventStream.publish(savedTranscript)).thenReturn(Mono.empty());
        when(transcriptRepository.findById(any(UUID.class))).thenReturn(Mono.just(transcriptRaw));

        Mono<Void> result = handler.processTranscriptEvent(transcriptRaw);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires deeper repository mocking")
    void testProcessTranscriptWithEmbeddingsSuccess() {
        UUID id = UUID.randomUUID();
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(id);

        when(transcriptWithEmbeddingsRepository.findById(id)).thenReturn(Mono.just(transcriptWithEmbeddings));
        when(transcriptWithEmbeddingsRepository.save(any(TranscriptWithEmbeddings.class)))
                .thenReturn(Mono.just(transcriptWithEmbeddings));
        when(transcriptWithEmbeddingsRepository.findById(any(UUID.class))).thenReturn(Mono.just(transcriptWithEmbeddings));
        when(transcriptRepository.findById(any(UUID.class))).thenReturn(Mono.just(new TranscriptRaw()));
        when(transcriptRepository.findById(any(UUID.class))).thenReturn(Mono.just(new TranscriptRaw()));
        when(transcriptRepository.findById(any(UUID.class))).thenReturn(Mono.just(new TranscriptRaw()));
        when(llmConfig.getMaxChunkTokens()).thenReturn(1000);
        when(embeddingService.embed(anyList())).thenReturn(ImmutableList.of(new float[]{1.0f}));
        doNothing().when(vectorStore).add(anyList());
        when(eventStream.publish(any(TranscriptWithEmbeddings.class))).thenReturn(Mono.empty());

        Mono<TranscriptWithEmbeddings> result = handler.processTranscriptWithEmbeddings(id);

        StepVerifier.create(result)
                .expectNext(transcriptWithEmbeddings)
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires deeper repository mocking")
    void testProcessTranscriptNullContent() {
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        TranscriptRaw transcriptRaw = new TranscriptRaw();
        transcriptRaw.setTranscript(null);

        when(transcriptRepository.findById(any(UUID.class))).thenReturn(Mono.just(transcriptRaw));
        when(transcriptWithEmbeddingsRepository.save(any(TranscriptWithEmbeddings.class)))
                .thenReturn(Mono.just(transcriptWithEmbeddings));

        Mono<TranscriptWithEmbeddings> result = handler.processTranscript(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .expectNext(transcriptWithEmbeddings)
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires deeper repository mocking")
    void testProcessTranscriptException() {
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());

        when(transcriptRepository.findById(any(UUID.class))).thenReturn(Mono.error(new RuntimeException("DB error")));

        Mono<TranscriptWithEmbeddings> result = handler.processTranscript(transcriptWithEmbeddings);

        StepVerifier.create(result)
                .expectNext(transcriptWithEmbeddings) // Should set status to FAILED
                .verifyComplete();
    }

    @Test
    void testDetermineIdealMaxChunkSizeForSingleTranscriptChunks() {
        when(llmConfig.getMaxChunkTokens()).thenReturn(2000);

        int result = handler.determineIdealMaxChunkSizeForSingleTranscriptChunks();

        assert result == 2000;
    }
}
