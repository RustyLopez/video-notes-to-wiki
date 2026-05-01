package com.chaostensor.video_notes_to_wiki.repository;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public interface TranscriptRepository extends ReactiveCrudRepository<TranscriptRaw, UUID> {

    Mono<TranscriptRaw> findByVideoPathAndHash(String videoPath, String hash);

    Mono<TranscriptRaw> findByHash(String hash);

    Mono<TranscriptRaw> findByVideoPath(String videoPath);
}