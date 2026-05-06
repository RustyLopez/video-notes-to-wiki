package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.service.TranscriptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptRawControllerTest {

    @Mock
    private TranscriptRepository transcriptRepository;

    @Mock
    private TranscriptService transcriptService;

    private TranscriptRawController controller;

    @BeforeEach
    void setUp() {
        controller = new TranscriptRawController(transcriptRepository, transcriptService);
    }

    @Test
    void get_shouldReturnOkWhenCompleted() {
        UUID id = UUID.randomUUID();
        TranscriptRaw transcript = new TranscriptRaw();
        transcript.setId(id);
        transcript.setStatus(LlmStatus.COMPLETED);
        transcript.setVideoPath("/path/to/video.mp4");

        when(transcriptRepository.findById(id)).thenReturn(Mono.just(transcript));

        Mono<ResponseEntity<DtoTranscriptRaw>> result = controller.get(id);

        StepVerifier.create(result)
                .expectNextMatches(entity -> entity.getStatusCode().is2xxSuccessful() && entity.getBody() != null)
                .verifyComplete();
    }

    @Test
    void get_shouldReturnAcceptedWhenProcessing() {
        UUID id = UUID.randomUUID();
        TranscriptRaw transcript = new TranscriptRaw();
        transcript.setId(id);
        transcript.setStatus(LlmStatus.PROCESSING);
        transcript.setVideoPath("/path/to/video.mp4");

        when(transcriptRepository.findById(id)).thenReturn(Mono.just(transcript));

        Mono<ResponseEntity<DtoTranscriptRaw>> result = controller.get(id);

        StepVerifier.create(result)
                .expectNextMatches(entity -> entity.getStatusCode().is2xxSuccessful() && entity.getStatusCode().value() == 202)
                .verifyComplete();
    }

    @Test
    void get_shouldReturnNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(transcriptRepository.findById(id)).thenReturn(Mono.empty());

        Mono<ResponseEntity<DtoTranscriptRaw>> result = controller.get(id);

        StepVerifier.create(result)
                .expectNextMatches(entity -> entity.getStatusCode().is4xxClientError())
                .verifyComplete();
    }
}
