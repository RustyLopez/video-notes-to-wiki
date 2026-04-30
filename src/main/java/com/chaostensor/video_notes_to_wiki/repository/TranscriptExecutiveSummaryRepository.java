package com.chaostensor.video_notes_to_wiki.repository;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import java.util.UUID;

@Repository
public interface TranscriptExecutiveSummaryRepository extends ReactiveCrudRepository<TranscriptExecutiveSummary, UUID> {

    @Query("SELECT * FROM transcript_executive_summary ORDER BY created_at LIMIT :limit")
    Flux<TranscriptExecutiveSummary> findAllLimited(int limit);
}