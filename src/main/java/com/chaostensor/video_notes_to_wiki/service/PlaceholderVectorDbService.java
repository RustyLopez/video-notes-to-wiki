package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Placeholder implementation of VectorDbService.
 * This is a no-op implementation until a proper vector database is integrated.
 */
@Service
public class PlaceholderVectorDbService implements VectorDbService {

    private final Map<String, List<TranscriptWithEmbeddings.ChunkEmbedding>> storedChunks = new HashMap<>();

    @Override
    public Mono<List<TranscriptWithEmbeddings.ChunkEmbedding>> saveChunkEmbeddings(String transcriptId, List<TranscriptWithEmbeddings.ChunkEmbedding> chunkEmbeddings) {
        // TODO: Implement actual vector database storage
        // For now, store in memory
        storedChunks.put(transcriptId, new ArrayList<>(chunkEmbeddings));
        System.out.println("Placeholder: Saved " + chunkEmbeddings.size() + " chunks for transcript " + transcriptId);

        return Mono.just(chunkEmbeddings);// todo expec tthe actual vector db integration to have returned a mono to map or update this contract to return what it returns.
    }

    @Override
    public List<VectorSearchResult> searchSimilar(float[] queryEmbedding, int topK) {
        // TODO: Implement actual vector similarity search
        // Return empty list for now
        return new ArrayList<>();
    }

    @Override
    public List<String> queryChunks(String transcriptId, List<float[]> queryEmbeddings, int maxPromptContextLength) {
        // TODO: Implement actual querying logic
        // For now, return all chunks for the transcript, ignoring queryEmbeddings and maxPromptContextLength
        List<TranscriptWithEmbeddings.ChunkEmbedding> chunks = storedChunks.get(transcriptId);
        if (chunks == null) {
            return new ArrayList<>();
        }
        return chunks.stream().map(TranscriptWithEmbeddings.ChunkEmbedding::chunk).toList();
    }

    @Override
    public List<float[]> getMostRelevantEmbeddings(int topK) {
        // TODO: Implement actual logic to determine most relevant embeddings
        // For now, return empty list as placeholder
        return new ArrayList<>();
    }

    @Override
    public List<String> queryAllChunks(List<float[]> queryEmbeddings, int maxPromptContextLength) {
        // TODO: Implement actual querying logic across all transcripts
        // For now, return all chunks from all stored transcripts, ignoring queryEmbeddings and maxPromptContextLength
        List<String> allChunks = new ArrayList<>();
        for (List<TranscriptWithEmbeddings.ChunkEmbedding> chunks : storedChunks.values()) {
            allChunks.addAll(chunks.stream().map(TranscriptWithEmbeddings.ChunkEmbedding::chunk).toList());
        }
        return allChunks;
    }

    @Override
    public void saveSummaryEmbedding(String transcriptId, float[] summaryEmbedding) {
        // TODO: Implement actual vector database storage
        // For now, store in memory (could add to the same map or a separate one)
        System.out.println("Placeholder: Saved summary embedding for transcript " + transcriptId);
    }

    @Override
    public ResolvedIds resolveChunksToIds(List<String> chunks) {
        // Placeholder implementation: assume IDs can be derived from chunk data
        // For now, return empty lists as this is a placeholder
        return new ResolvedIds(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
}