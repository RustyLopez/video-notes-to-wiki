package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.event.EventStream;
import com.chaostensor.video_notes_to_wiki.config.VideoConfig;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
    private final VideoConfig videoConfig;
    private final EventStream<TranscriptRaw> eventStream;
    private final TranscriptRepository transcriptRepository;
    private Disposable subscription;

    public VideoProcessingScheduler(TranscriptService transcriptService,
                                    VideoConfig videoConfig,
                                    EventStream<TranscriptRaw> eventStream,
                                    TranscriptRepository transcriptRepository) {
        this.transcriptService = transcriptService;
        this.videoConfig = videoConfig;
        this.eventStream = eventStream;
        this.transcriptRepository = transcriptRepository;
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
                .flatMap(this::moveFileToTranscribed)
                .subscribe(
                        null, // onNext
                        error -> logger.error("Error in video processing subscription", error),
                        () -> logger.info("Video processing subscription completed")
                );
        logger.info("Subscribed to transcript event stream for file moving");
    }

    @Scheduled(fixedDelay = 60000) // Every minute
    public void scanAndProcessVideos() {
        Path dropDir = Paths.get(videoConfig.getDropDirectory());
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

    private reactor.core.publisher.Mono<Void> moveFileToTranscribed(TranscriptRaw transcript) {
        try {
            Path source = Paths.get(transcript.getVideoPath());
            Path targetDir = Paths.get(videoConfig.getTranscribedDirectory());
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            Path target = targetDir.resolve(source.getFileName());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Moved video file to transcribed directory: {}", target);
        } catch (IOException e) {
            logger.error("Failed to move video file: {}", transcript.getVideoPath(), e);
        }
        return reactor.core.publisher.Mono.empty();
    }
}