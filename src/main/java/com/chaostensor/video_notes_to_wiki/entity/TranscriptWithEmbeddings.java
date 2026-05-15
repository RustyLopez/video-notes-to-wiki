package com.chaostensor.video_notes_to_wiki.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.ImmutableList;

@Table("transcript_with_embeddings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptWithEmbeddings {

    @Id
    private UUID id;
    private UUID transcriptRawId;
    private ChunkEmbeddingList chunkEmbeddings;
    private LlmStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ImmutableList<ChunkEmbedding> getChunkEmbeddings() {
        return chunkEmbeddings != null && chunkEmbeddings.getItems() != null ? ImmutableList.copyOf(chunkEmbeddings.getItems()) : ImmutableList.of();
    }


}