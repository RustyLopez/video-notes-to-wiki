package com.chaostensor.video_notes_to_wiki.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Table("transcript_with_embeddings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptWithEmbeddings {

    public record ChunkEmbedding(String chunk, List<Float> embedding) {}

    @Id
    private UUID id;
    private UUID transcriptRawId;
    private List<ChunkEmbedding> chunkEmbeddings;
    private LlmStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public List<ChunkEmbedding> getChunkEmbeddings() {
        return chunkEmbeddings != null ? Collections.unmodifiableList(chunkEmbeddings) : Collections.emptyList();
    }


}