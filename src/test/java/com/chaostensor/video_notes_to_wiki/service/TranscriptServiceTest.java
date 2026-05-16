package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.event.EventStream;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranscriptServiceTest {

    @Mock
    private TranscriptRepository transcriptRepository;

    @Mock
    private WhisperService whisperService;

    @Mock
    private EventStream<TranscriptRaw> eventStream;

    private TranscriptService transcriptService;

    @BeforeEach
    void setUp() {
        transcriptService = new TranscriptService(transcriptRepository, whisperService, eventStream);
    }

    @Test
    void createTranscript_shouldReturnErrorWhenHashComputationFails() throws IOException {
        // Test the branch where computeFileHash throws an exception
        final String invalidVideoPath = "/nonexistent/path/video.mp4";

        // Since computeFileHash tries to read the file, it will fail with IOException
        final var result = transcriptService.createTranscript(invalidVideoPath);

        StepVerifier.create(result.getInitiation())
                .expectError(IOException.class)
                .verify();
    }

    @Test
    void createTranscript_shouldReturnEmptyWhenDuplicateVideoPathAndHash() throws IOException {
        // Create a temporary file for testing
        final Path tempFile = Files.createTempFile("test-video", ".mp4");
        Files.write(tempFile, "test content".getBytes());
        final String videoPath = tempFile.toString();

        try {
            final String expectedHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72";
            final TranscriptRaw existingTranscript = new TranscriptRaw();
            existingTranscript.setVideoPath(videoPath);
            existingTranscript.setHash(expectedHash);

            when(transcriptRepository.findByVideoPathAndHash(any(), any()))
                    .thenReturn(Mono.just(existingTranscript));
            when(transcriptRepository.findByHash(any()))
                    .thenReturn(Mono.empty());

            final var result = transcriptService.createTranscript(videoPath);

            StepVerifier.create(result.getInitiation())
                    .expectComplete();

            verify(transcriptRepository).findByVideoPathAndHash(videoPath, expectedHash);
            verifyNoMoreInteractions(transcriptRepository);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void createTranscript_shouldCreateNewTranscriptWhenHashExistsAtDifferentPath() throws IOException {
        // Create a temporary file for testing
        final Path tempFile = Files.createTempFile("test-video", ".mp4");
        Files.write(tempFile, "test content".getBytes());
        final String videoPath = tempFile.toString();

        try {
            final String expectedHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72";
            final TranscriptRaw existingTranscript = new TranscriptRaw();
            existingTranscript.setVideoPath("/different/path/video.mp4");
            existingTranscript.setHash(expectedHash);

            when(transcriptRepository.findByVideoPathAndHash(videoPath, expectedHash))
                    .thenReturn(Mono.empty());
            when(transcriptRepository.findByHash(expectedHash))
                    .thenReturn(Mono.just(existingTranscript));

            final TranscriptRaw savedTranscript = new TranscriptRaw();
            savedTranscript.setId(UUID.randomUUID());
            savedTranscript.setVideoPath(videoPath);
            savedTranscript.setHash(expectedHash);
            savedTranscript.setStatus(LlmStatus.PENDING);

            when(transcriptRepository.save(any(TranscriptRaw.class)))
                    .thenReturn(Mono.just(savedTranscript));

            final var result = transcriptService.createTranscript(videoPath);

            StepVerifier.create(result.getInitiation())
                    .expectNext(savedTranscript)
                    .verifyComplete();

            verify(transcriptRepository).findByVideoPathAndHash(videoPath, expectedHash);
            verify(transcriptRepository).findByHash(expectedHash);
            verify(transcriptRepository, times(2)).save(any(TranscriptRaw.class));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void createTranscript_shouldCreateNewTranscriptWhenNoExisting() throws IOException {
        // Create a temporary file for testing
        final Path tempFile = Files.createTempFile("test-video", ".mp4");
        Files.write(tempFile, "test content".getBytes());
        final String videoPath = tempFile.toString();

        try {
            final String expectedHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72";

            when(transcriptRepository.findByVideoPathAndHash(videoPath, expectedHash))
                    .thenReturn(Mono.empty());
            when(transcriptRepository.findByHash(expectedHash))
                    .thenReturn(Mono.empty());

            final TranscriptRaw savedTranscript = new TranscriptRaw();
            savedTranscript.setId(UUID.randomUUID());
            savedTranscript.setVideoPath(videoPath);
            savedTranscript.setHash(expectedHash);
            savedTranscript.setStatus(LlmStatus.PENDING);

            when(transcriptRepository.save(any(TranscriptRaw.class)))
                    .thenReturn(Mono.just(savedTranscript));

            final var result = transcriptService.createTranscript(videoPath);

            StepVerifier.create(result.getInitiation())
                    .expectNext(savedTranscript)
                    .verifyComplete();

            verify(transcriptRepository).findByVideoPathAndHash(videoPath, expectedHash);
            verify(transcriptRepository).findByHash(expectedHash);
            verify(transcriptRepository, times(2)).save(any(TranscriptRaw.class));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void createTranscript_shouldProcessTranscriptSuccessfully() throws IOException {
        // Create a temporary file for testing
        final Path tempFile = Files.createTempFile("test-video", ".mp4");
        Files.write(tempFile, "test content".getBytes());
        final String videoPath = tempFile.toString();

        try {
            final String expectedHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72";

            when(transcriptRepository.findByVideoPathAndHash(videoPath, expectedHash))
                    .thenReturn(Mono.empty());
            when(transcriptRepository.findByHash(expectedHash))
                    .thenReturn(Mono.empty());

            // Mock the initial save for PENDING status
            final TranscriptRaw pendingTranscript = new TranscriptRaw();
            pendingTranscript.setId(UUID.randomUUID());
            pendingTranscript.setVideoPath(videoPath);
            pendingTranscript.setHash(expectedHash);
            pendingTranscript.setStatus(LlmStatus.PENDING);

            // Mock the save for PROCESSING status
            final TranscriptRaw processingTranscript = new TranscriptRaw();
            processingTranscript.setId(pendingTranscript.getId());
            processingTranscript.setVideoPath(videoPath);
            processingTranscript.setHash(expectedHash);
            processingTranscript.setStatus(LlmStatus.PROCESSING);

            // Mock the final save for COMPLETED status
            final TranscriptRaw completedTranscript = new TranscriptRaw();
            completedTranscript.setId(pendingTranscript.getId());
            completedTranscript.setVideoPath(videoPath);
            completedTranscript.setHash(expectedHash);
            completedTranscript.setStatus(LlmStatus.COMPLETED);
            completedTranscript.setTranscriptRaw("Transcribed text");

            when(transcriptRepository.save(any(TranscriptRaw.class)))
                    .thenReturn(Mono.just(pendingTranscript))  // First save (PENDING)
                    .thenReturn(Mono.just(processingTranscript))  // Second save (PROCESSING)
                    .thenReturn(Mono.just(completedTranscript));  // Third save (COMPLETED)

            when(whisperService.transcribeVideo(videoPath))
                    .thenReturn(Mono.just("Transcribed text"));

            when(eventStream.publish(completedTranscript))
                    .thenReturn(Mono.empty());

            final var result = transcriptService.createTranscript(videoPath);

            StepVerifier.create(result.getCompletion())
                    .expectNext(completedTranscript)
                    .verifyComplete();

            // Verify that async processing was triggered
            verify(whisperService).transcribeVideo(videoPath);
            verify(eventStream).publish(completedTranscript);

            // Verify all saves were called
            verify(transcriptRepository, times(3)).save(any(TranscriptRaw.class));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void createTranscript_shouldHandleTranscriptionFailure() throws IOException {
        // Create a temporary file for testing
        final Path tempFile = Files.createTempFile("test-video", ".mp4");
        Files.write(tempFile, "test content".getBytes());
        final String videoPath = tempFile.toString();

        try {
            final String expectedHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72";

            when(transcriptRepository.findByVideoPathAndHash(videoPath, expectedHash))
                    .thenReturn(Mono.empty());
            when(transcriptRepository.findByHash(expectedHash))
                    .thenReturn(Mono.empty());

            // Mock the initial save for PENDING status
            final TranscriptRaw pendingTranscript = new TranscriptRaw();
            pendingTranscript.setId(UUID.randomUUID());
            pendingTranscript.setVideoPath(videoPath);
            pendingTranscript.setHash(expectedHash);
            pendingTranscript.setStatus(LlmStatus.PENDING);

            // Mock the save for PROCESSING status
            final TranscriptRaw processingTranscript = new TranscriptRaw();
            processingTranscript.setId(pendingTranscript.getId());
            processingTranscript.setVideoPath(videoPath);
            processingTranscript.setHash(expectedHash);
            processingTranscript.setStatus(LlmStatus.PROCESSING);

            // Mock the final save for FAILED status
            final TranscriptRaw failedTranscript = new TranscriptRaw();
            failedTranscript.setId(pendingTranscript.getId());
            failedTranscript.setVideoPath(videoPath);
            failedTranscript.setHash(expectedHash);
            failedTranscript.setStatus(LlmStatus.FAILED);

            when(transcriptRepository.save(any(TranscriptRaw.class)))
                    .thenReturn(Mono.just(pendingTranscript))  // First save (PENDING)
                    .thenReturn(Mono.just(processingTranscript))  // Second save (PROCESSING)
                    .thenReturn(Mono.just(failedTranscript));  // Third save (FAILED)

            final RuntimeException transcriptionError = new RuntimeException("Transcription failed");
            when(whisperService.transcribeVideo(videoPath))
                    .thenReturn(Mono.error(transcriptionError));

            final var result = transcriptService.createTranscript(videoPath);

            StepVerifier.create(result.getCompletion())
                    .expectNext(failedTranscript)
                    .verifyComplete();

            // Verify that async processing was triggered but failed
            verify(whisperService).transcribeVideo(videoPath);
            verify(eventStream, never()).publish(any());

            // Verify all saves were called
            verify(transcriptRepository, times(3)).save(any(TranscriptRaw.class));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void createTranscript_shouldReturnErrorWhenInitialSaveFails() throws IOException {
        // Create a temporary file for testing
        final Path tempFile = Files.createTempFile("test-video", ".mp4");
        Files.write(tempFile, "test content".getBytes());
        final String videoPath = tempFile.toString();

        try {
            final String expectedHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72";

            when(transcriptRepository.findByVideoPathAndHash(videoPath, expectedHash))
                    .thenReturn(Mono.empty());
            when(transcriptRepository.findByHash(expectedHash))
                    .thenReturn(Mono.empty());

            final RuntimeException saveError = new RuntimeException("Initial save failed");
            when(transcriptRepository.save(any(TranscriptRaw.class)))
                    .thenReturn(Mono.error(saveError));

            final var result = transcriptService.createTranscript(videoPath);

            StepVerifier.create(result.getInitiation())
                    .expectError(RuntimeException.class)
                    .verify();

            verify(transcriptRepository).findByVideoPathAndHash(videoPath, expectedHash);
            verify(transcriptRepository).findByHash(expectedHash);
            verify(transcriptRepository).save(any(TranscriptRaw.class));
            verifyNoMoreInteractions(transcriptRepository, whisperService, eventStream);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void createTranscript_shouldHandleProcessingSaveFailure() throws IOException {
        // Create a temporary file for testing
        final Path tempFile = Files.createTempFile("test-video", ".mp4");
        Files.write(tempFile, "test content".getBytes());
        final String videoPath = tempFile.toString();

        try {
            final String expectedHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72";

            when(transcriptRepository.findByVideoPathAndHash(videoPath, expectedHash))
                    .thenReturn(Mono.empty());
            when(transcriptRepository.findByHash(expectedHash))
                    .thenReturn(Mono.empty());

            // Mock the initial save for PENDING status
            final TranscriptRaw pendingTranscript = new TranscriptRaw();
            pendingTranscript.setId(UUID.randomUUID());
            pendingTranscript.setVideoPath(videoPath);
            pendingTranscript.setHash(expectedHash);
            pendingTranscript.setStatus(LlmStatus.PENDING);

            final RuntimeException saveError = new RuntimeException("Processing save failed");
            when(transcriptRepository.save(any(TranscriptRaw.class)))
                    .thenReturn(Mono.just(pendingTranscript))  // First save (PENDING)
                    .thenReturn(Mono.error(saveError));  // Second save (PROCESSING) fails

            final var result = transcriptService.createTranscript(videoPath);

            StepVerifier.create(result.getInitiation())
                    .expectNext(pendingTranscript)
                    .verifyComplete();

            // Verify that async processing attempted but save failed, so transcribe not called
            verify(whisperService, never()).transcribeVideo(any());
            verify(eventStream, never()).publish(any());

            // Verify saves were called
            verify(transcriptRepository, times(2)).save(any(TranscriptRaw.class));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void createTranscript_shouldHandleCompletedSaveFailure() throws IOException {
        // Create a temporary file for testing
        final Path tempFile = Files.createTempFile("test-video", ".mp4");
        Files.write(tempFile, "test content".getBytes());
        final String videoPath = tempFile.toString();

        try {
            final String expectedHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72";

            when(transcriptRepository.findByVideoPathAndHash(videoPath, expectedHash))
                    .thenReturn(Mono.empty());
            when(transcriptRepository.findByHash(expectedHash))
                    .thenReturn(Mono.empty());

            // Mock the initial save for PENDING status
            final TranscriptRaw pendingTranscript = new TranscriptRaw();
            pendingTranscript.setId(UUID.randomUUID());
            pendingTranscript.setVideoPath(videoPath);
            pendingTranscript.setHash(expectedHash);
            pendingTranscript.setStatus(LlmStatus.PENDING);

            // Mock the save for PROCESSING status
            final TranscriptRaw processingTranscript = new TranscriptRaw();
            processingTranscript.setId(pendingTranscript.getId());
            processingTranscript.setVideoPath(videoPath);
            processingTranscript.setHash(expectedHash);
            processingTranscript.setStatus(LlmStatus.PROCESSING);

            final RuntimeException saveError = new RuntimeException("Completed save failed");
            when(transcriptRepository.save(any(TranscriptRaw.class)))
                    .thenReturn(Mono.just(pendingTranscript))  // First save (PENDING)
                    .thenReturn(Mono.just(processingTranscript))  // Second save (PROCESSING)
                    .thenReturn(Mono.error(saveError));  // Third save (COMPLETED) fails

            final RuntimeException transcriptionError = new RuntimeException("Transcription failed");
            when(whisperService.transcribeVideo(videoPath))
                    .thenReturn(Mono.error(transcriptionError));

            final var result = transcriptService.createTranscript(videoPath);

            StepVerifier.create(result.getInitiation())
                    .expectNext(pendingTranscript)
                    .verifyComplete();

            // Verify that transcription was called but save failed, so onErrorResume saved FAILED, event not published
            verify(whisperService).transcribeVideo(videoPath);
            verify(eventStream, never()).publish(any());

            // Verify all saves were called
            verify(transcriptRepository, times(4)).save(any(TranscriptRaw.class));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void createTranscript_shouldHandleFailedSaveFailure() throws IOException {
        // Create a temporary file for testing
        final Path tempFile = Files.createTempFile("test-video", ".mp4");
        Files.write(tempFile, "test content".getBytes());
        final String videoPath = tempFile.toString();

        try {
            final String expectedHash = "6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72";

            when(transcriptRepository.findByVideoPathAndHash(videoPath, expectedHash))
                    .thenReturn(Mono.empty());
            when(transcriptRepository.findByHash(expectedHash))
                    .thenReturn(Mono.empty());

            // Mock the initial save for PENDING status
            final TranscriptRaw pendingTranscript = new TranscriptRaw();
            pendingTranscript.setId(UUID.randomUUID());
            pendingTranscript.setVideoPath(videoPath);
            pendingTranscript.setHash(expectedHash);
            pendingTranscript.setStatus(LlmStatus.PENDING);

            // Mock the save for PROCESSING status
            final TranscriptRaw processingTranscript = new TranscriptRaw();
            processingTranscript.setId(pendingTranscript.getId());
            processingTranscript.setVideoPath(videoPath);
            processingTranscript.setHash(expectedHash);
            processingTranscript.setStatus(LlmStatus.PROCESSING);

            final RuntimeException saveError = new RuntimeException("Failed save failed");
            when(transcriptRepository.save(any(TranscriptRaw.class)))
                    .thenReturn(Mono.just(pendingTranscript))  // First save (PENDING)
                    .thenReturn(Mono.just(processingTranscript))  // Second save (PROCESSING)
                    .thenReturn(Mono.error(saveError));  // Third save (FAILED) fails

            final RuntimeException transcriptionError = new RuntimeException("Transcription failed");
            when(whisperService.transcribeVideo(videoPath))
                    .thenReturn(Mono.error(transcriptionError));

            final var result = transcriptService.createTranscript(videoPath);

            StepVerifier.create(result.getInitiation())
                    .expectNext(pendingTranscript)
                    .verifyComplete();

            // Verify that transcription was called and failed, save for FAILED also failed
            verify(whisperService).transcribeVideo(videoPath);
            verify(eventStream, never()).publish(any());

            // Verify all saves were called
            verify(transcriptRepository, times(3)).save(any(TranscriptRaw.class));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}