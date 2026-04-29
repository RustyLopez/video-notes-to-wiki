package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
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

    public Mono<TranscriptRaw> processTranscript(TranscriptRaw transcriptRaw) {
        transcriptRaw.setStatus(LlmStatus.PROCESSING);
        return transcriptRepository.save(transcriptRaw)
            .flatMap(savedTranscript -> {
                return whisperService.transcribeVideo(savedTranscript.getVideoPath())
                    .flatMap(transcriptText -> {
                        savedTranscript.setTranscript(transcriptText);
                        savedTranscript.setStatus(LlmStatus.COMPLETED);
                        return transcriptRepository.save(savedTranscript);
                    })
                    .onErrorResume(e -> {
                        savedTranscript.setStatus(LlmStatus.FAILED);
                        return transcriptRepository.save(savedTranscript);
                    });
            });
    }


}