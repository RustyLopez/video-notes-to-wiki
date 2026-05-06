package com.chaostensor.video_notes_to_wiki.service;

import io.jchunk.core.chunk.Chunk;
import lombok.RequiredArgsConstructor;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    private final String preferredEmbeddingModel;

    public EmbeddingService(final EmbeddingModel embeddingModel, @Value("${app.llm.embeddings.models.preferred}") final String preferredEmbeddingModel) {
        this.embeddingModel = embeddingModel;
        this.preferredEmbeddingModel = preferredEmbeddingModel;
    }

    public List<float[]> embed(final List<String> chunks) {

        return this.embeddingModel.call(
                new EmbeddingRequest(chunks,
                        OllamaEmbeddingOptions.builder()
                                .model(preferredEmbeddingModel)
                                .truncate(false)
                                .build())
        ).getResults().stream().map(Embedding::getOutput).collect(Collectors.toList());


    }
}