package com.chaostensor.video_notes_to_wiki.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EventHandlerTranscriptRawToTranscriptWithEmbeddingsTest {

    @Test
    void handler_shouldBeInstantiableWithMocks() {
        // We avoid full wiring as most logic is private + @PostConstruct
        // This provides basic coverage for constructor
        assertDoesNotThrow(() -> {
            new EventHandlerTranscriptRawToTranscriptWithEmbeddings(
                    mock(EventStream.class),
                    mock(com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository.class),
                    mock(com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository.class),
                    mock(com.chaostensor.video_notes_to_wiki.config.LlmConfig.class),
                    mock(com.chaostensor.video_notes_to_wiki.service.EmbeddingService.class),
                    mock(org.springframework.ai.vectorstore.VectorStore.class),
                    mock(EventStream.class)
            );
        });
    }

    private void assertDoesNotThrow(Runnable r) {
        r.run();
    }
}
