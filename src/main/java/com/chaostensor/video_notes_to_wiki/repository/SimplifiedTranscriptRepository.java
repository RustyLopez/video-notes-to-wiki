package com.chaostensor.video_notes_to_wiki.repository;

import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import reactor.core.publisher.Mono;

@Repository
public interface SimplifiedTranscriptRepository extends ReactiveCrudRepository<SimplifiedTranscript, UUID> {
    Mono<SimplifiedTranscript> findByJobIdAndTranscriptSubId(UUID jobId, String transcriptSubId);
}