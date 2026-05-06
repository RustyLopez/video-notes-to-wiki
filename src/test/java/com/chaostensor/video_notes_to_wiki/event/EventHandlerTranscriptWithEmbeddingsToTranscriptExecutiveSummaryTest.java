package com.chaostensor.video_notes_to_wiki.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummaryTest {

    @Test
    void handler_shouldBeInstantiableWithMocks() {
        assertDoesNotThrow(() -> {
            new EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummary(
                    mock(EventStream.class),
                    mock(com.chaostensor.video_notes_to_wiki.repository.TranscriptExecutiveSummaryRepository.class),
                    mock(com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository.class),
                    mock(org.springframework.web.reactive.function.client.WebClient.Builder.class),
                    mock(EventStream.class),
                    mock(com.chaostensor.video_notes_to_wiki.config.LlmConfig.class),
                    mock(org.springframework.ai.vectorstore.VectorStore.class)
            );
        });
    }

    private void assertDoesNotThrow(Runnable r) { r.run(); }
}
