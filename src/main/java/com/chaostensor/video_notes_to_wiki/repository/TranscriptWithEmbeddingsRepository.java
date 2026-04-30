package com.chaostensor.video_notes_to_wiki.repository;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TranscriptWithEmbeddingsRepository extends ReactiveCrudRepository<TranscriptWithEmbeddings, UUID> {

}