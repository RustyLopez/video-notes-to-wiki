package com.chaostensor.video_notes_to_wiki.dto;

import com.chaostensor.video_notes_to_wiki.entity.ChunkEmbedding;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ChunkEmbeddingList {
    ImmutableList<ChunkEmbedding> items;

    public static ChunkEmbeddingList of(List<ChunkEmbedding> items) {
        return ChunkEmbeddingList.builder()
                .items(ImmutableList.copyOf(items))
                .build();
    }
}