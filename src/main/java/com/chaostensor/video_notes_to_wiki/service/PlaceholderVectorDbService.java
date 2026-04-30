package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Placeholder implementation of VectorDbService.
 * This is a no-op implementation until a proper vector database is integrated.
 */
@Service
public class PlaceholderVectorDbService implements VectorDbService {

    @Override
    public void saveChunkEmbeddings(String transcriptId, List<TranscriptWithEmbeddings.ChunkEmbedding> chunkEmbeddings) {
        // TODO: Implement actual vector database storage
        // For now, just log that chunks would be saved
        System.out.println("Placeholder: Would save " + chunkEmbeddings.size() + " chunks for transcript " + transcriptId);
    }

    @Override
    public List<VectorSearchResult> searchSimilar(float[] queryEmbedding, int topK) {
        // TODO: Implement actual vector similarity search
        // Return empty list for now
        return new ArrayList<>();
    }
}