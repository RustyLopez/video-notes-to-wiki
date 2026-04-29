package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.Transcript;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptStatus;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TranscriptionService {

    private final WhisperService whisperService;
    private final TranscriptRepository transcriptRepository;

    public TranscriptionService(WhisperService whisperService, TranscriptRepository transcriptRepository) {
        this.whisperService = whisperService;
        this.transcriptRepository = transcriptRepository;
    }

    public Mono<Transcript> processTranscript(Transcript transcript) {
        transcript.setStatus(TranscriptStatus.PROCESSING);
        return transcriptRepository.save(transcript)
            .flatMap(savedTranscript -> {
                return whisperService.transcribeVideo(savedTranscript.getVideoPath())
                    .flatMap(transcriptText -> {
                        savedTranscript.setTranscript(transcriptText);
                        savedTranscript.setStatus(TranscriptStatus.COMPLETED);
                        return transcriptRepository.save(savedTranscript);
                    })
                    .onErrorResume(e -> {
                        savedTranscript.setStatus(TranscriptStatus.FAILED);
                        return transcriptRepository.save(savedTranscript);
                    });
            });
    }


}