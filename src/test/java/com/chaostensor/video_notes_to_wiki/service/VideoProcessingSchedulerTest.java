package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.event.EventStream;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoProcessingSchedulerTest {

    @Mock
    private TranscriptService transcriptService;

    @Mock
    private EventStream<TranscriptRaw> eventStream;

    @Mock
    private TranscriptRepository transcriptRepository;

    private VideoProcessingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new VideoProcessingScheduler(transcriptService, eventStream, "media-input", transcriptRepository);
    }

    @Test
    void isVideoFile_shouldReturnTrueForVideoExtensions() {
        assertTrue(scheduler.isVideoFile(Paths.get("test.mp4")));
        assertTrue(scheduler.isVideoFile(Paths.get("test.avi")));
        assertTrue(scheduler.isVideoFile(Paths.get("test.mov")));
        assertTrue(scheduler.isVideoFile(Paths.get("test.mkv")));
    }

    @Test
    void isVideoFile_shouldReturnFalseForNonVideoExtensions() {
        assertFalse(scheduler.isVideoFile(Paths.get("test.txt")));
        assertFalse(scheduler.isVideoFile(Paths.get("test.jpg")));
        assertFalse(scheduler.isVideoFile(Paths.get("test")));
    }

    @Test
    void scanAndProcessVideos_shouldSkipNonExistingDir() {
        scheduler = new VideoProcessingScheduler(transcriptService, eventStream, "nonexistent-dir-12345", transcriptRepository);
        scheduler.scanAndProcessVideos();
        verifyNoInteractions(transcriptService, transcriptRepository);
    }
}
