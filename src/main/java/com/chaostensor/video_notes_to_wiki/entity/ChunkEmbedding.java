package com.chaostensor.video_notes_to_wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkEmbedding {
    private String chunk;
    private float[] embedding;
}
