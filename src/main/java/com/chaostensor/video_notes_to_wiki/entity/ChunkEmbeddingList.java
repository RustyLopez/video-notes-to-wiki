package com.chaostensor.video_notes_to_wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkEmbeddingList {
    private List<ChunkEmbedding> items;
}