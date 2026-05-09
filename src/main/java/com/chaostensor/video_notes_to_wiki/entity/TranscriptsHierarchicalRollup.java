package com.chaostensor.video_notes_to_wiki.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

import com.google.common.collect.ImmutableList;

/**
 * NOTE may be very large we  probably want to .. partition and expire rows here. Could get away
 * with only saving the latest since it's all derived but, may be nice to have some history.
 */
@Table("transcripts_hierarchical_rollup")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptsHierarchicalRollup {

    @Id
    private UUID id;
    private String compressedResult;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private ImmutableList<TranscriptWithEmbeddings.ChunkEmbedding> chunksWithEmbeddings;


}