package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.Job;
import com.chaostensor.video_notes_to_wiki.entity.JobStatus;
import com.chaostensor.video_notes_to_wiki.repository.JobRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VideoProcessingService {

    private final WhisperService whisperService;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public VideoProcessingService(WhisperService whisperService, JobRepository jobRepository, ObjectMapper objectMapper) {
        this.whisperService = whisperService;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    public Mono<Job> processJob(Job job) {
        job.setStatus(JobStatus.PROCESSING);
        return jobRepository.save(job)
            .flatMap(savedJob -> {
                Map<String, String> videoFiles = findVideoFiles(savedJob.getInputDir());
                return whisperService.transcribeVideos(videoFiles)
                    .flatMap(transcripts -> {

                            savedJob.setTranscriptsJson(objectMapper.writeValueAsString(transcripts));
                            savedJob.setStatus(JobStatus.COMPLETED);
                            return jobRepository.save(savedJob);

                    })
                    .onErrorResume(e -> {
                        savedJob.setStatus(JobStatus.FAILED);
                        return jobRepository.save(savedJob);
                    });
            });
    }

    private Map<String, String> findVideoFiles(String dir) {
        try {
            return Files.walk(Paths.get(dir))
                .filter(Files::isRegularFile)
                .filter(path -> isVideoFile(path))
                .collect(Collectors.toMap(
                    path -> path.getFileName().toString(),
                    Path::toString
                ));
        } catch (IOException e) {
            throw new RuntimeException("Error finding video files", e);
        }
    }

    private boolean isVideoFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".mkv") ||
               fileName.endsWith(".mov") || fileName.endsWith(".wmv");
    }
}