package com.chaostensor.video_notes_to_wiki.dto;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import lombok.Builder;
import lombok.Value;
import reactor.core.publisher.Mono;

@Value
@Builder
public class TranscriptCreationResult {
    private final Mono<TranscriptRaw> initiation;
    private final Mono<TranscriptRaw> completion;
}
