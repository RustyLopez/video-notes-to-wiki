package com.chaostensor.video_notes_to_wiki.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public List<List<Float>> embedTexts(List<String> texts) {
        List<float[]> embeddings = embeddingModel.embed(texts);
        return embeddings.stream()
                .map(arr -> {
                    List<Float> list = new java.util.ArrayList<>();
                    for (float f : arr) {
                        list.add(f);
                    }
                    return list;
                })
                .collect(Collectors.toList());
    }
}