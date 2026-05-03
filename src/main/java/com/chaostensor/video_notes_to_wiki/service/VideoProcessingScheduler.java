package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.event.EventStream;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.Disposable;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;


@Component
public class VideoProcessingScheduler {

    private static final Logger logger = LoggerFactory.getLogger(VideoProcessingScheduler.class);

    private final TranscriptService transcriptService;
    private final EventStream<TranscriptRaw> eventStream;
    private final TranscriptRepository transcriptRepository;
    private final String mediaInput;
    private final String videoHasBeenTranscribedDir;
    private Disposable subscription;

    public VideoProcessingScheduler(TranscriptService transcriptService,
                                    EventStream<TranscriptRaw> eventStream,
                                    @Value("${app.media-input}")
                                    String mediaInput,
                                    TranscriptRepository transcriptRepository) {
        this.transcriptService = transcriptService;
        this.eventStream = eventStream;
        this.transcriptRepository = transcriptRepository;
        this.mediaInput = mediaInput;
        this.videoHasBeenTranscribedDir = videoHasBeenTranscribedDir;
    }

    @PostConstruct
    public void subscribe() {
        /*
         * NOTE this doesn't quite work ? We can't guarantee all videos were dropped to this scheduler.
         * If we delete the controller.
         *
         * Well no even the controller assumes there is an already accessible file on the server.
         *
         * SO this would actually work there.
         *
         * BUT then that makes the controller 100% redundant unless we convert it to an upload handler.
         */
        subscription = eventStream.getEventStream()
                .filter(transcript -> transcript.getStatus() == LlmStatus.COMPLETED)
                .subscribe(
                        null, // onNext
                        error -> logger.error("Error in video processing subscription", error),
                        () -> logger.info("Video processing subscription completed")
                );
        logger.info("Subscribed to transcript event stream for file moving");
    }

    @Scheduled(fixedDelay = 60000) // Every minute
    public void scanAndProcessVideos() {
        Path dropDir = Paths.get(mediaInput);
        if (!Files.exists(dropDir)) {
            logger.warn("Video drop directory does not exist: {}", dropDir);
            return;
        }

        try (Stream<Path> paths = Files.walk(dropDir)) {
            List<Path> videoFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isVideoFile)
                    .toList();

            for (Path videoFile : videoFiles) {
                String videoPath = videoFile.toString();
                transcriptRepository.findByVideoPath(videoPath)
                        .hasElement()
                        .flatMap(exists -> {
                            if (!exists) {
                                logger.info("Processing new video file: {}", videoPath);
                                return transcriptService.createTranscript(videoPath);
                            } else {
                                logger.debug("Video file already processed: {}", videoPath);
                                return reactor.core.publisher.Mono.empty();
                            }
                        })
                        .subscribe();
            }
        } catch (IOException e) {
            logger.error("Error scanning video drop directory", e);
        }
    }

    private boolean isVideoFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".mov") || fileName.endsWith(".mkv");
    }


}