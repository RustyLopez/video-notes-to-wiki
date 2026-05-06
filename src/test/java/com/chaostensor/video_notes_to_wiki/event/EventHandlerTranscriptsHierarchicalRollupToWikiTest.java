package com.chaostensor.video_notes_to_wiki.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventHandlerTranscriptsHierarchicalRollupToWikiTest {

    @Test
    void handler_shouldBeInstantiableWithMocks() {
        org.springframework.web.reactive.function.client.WebClient.Builder builder = mock(org.springframework.web.reactive.function.client.WebClient.Builder.class);
        org.springframework.web.reactive.function.client.WebClient webClient = mock(org.springframework.web.reactive.function.client.WebClient.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);
        assertDoesNotThrow(() -> {
            new EventHandlerTranscriptsHierarchicalRollupToWiki(
                    mock(EventStream.class),
                    builder,
                    mock(com.chaostensor.video_notes_to_wiki.repository.WikiRepository.class),
                    mock(EventStream.class),
                    mock(org.springframework.ai.vectorstore.VectorStore.class)
            );
        });
    }

    private void assertDoesNotThrow(Runnable r) { r.run(); }
}
