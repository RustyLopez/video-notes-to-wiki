package com.chaostensor.video_notes_to_wiki.repository;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptsHierarchicalRollup;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TranscriptsHierarchicalRollupRepository extends ReactiveCrudRepository<TranscriptsHierarchicalRollup, UUID> {

}