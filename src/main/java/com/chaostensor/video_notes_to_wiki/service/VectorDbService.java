package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;

import java.util.List;
import java.util.UUID;

/**
 * Generic Vector Database service interface for storing and querying vector embeddings.
 * This is a placeholder interface until a proper vector database solution is determined.
 */
public interface VectorDbService {

    /**
     * Saves chunk embeddings to the vector database.
     *
     * @param transcriptId The ID of the transcript these chunks belong to
     * @param chunkEmbeddings List of chunk embeddings to save
     */
    void saveChunkEmbeddings(String transcriptId, List<TranscriptWithEmbeddings.ChunkEmbedding> chunkEmbeddings);

    /**
     * Saves summary embedding to the vector database.
     *
     * @param transcriptId The ID of the transcript this summary belongs to
     * @param summaryEmbedding The embedding of the executive summary
     */
    void saveSummaryEmbedding(String transcriptId, float[] summaryEmbedding);

    /**
     * Searches for similar chunks based on vector similarity.
     *
     * @param queryEmbedding The query embedding vector
     * @param topK Number of top similar results to return
     * @return List of similar chunks with their similarity scores
     */
    List<VectorSearchResult> searchSimilar(float[] queryEmbedding, int topK);

    /**
     * Queries chunks for a specific transcript based on chunk embeddings.
     *
     * @param transcriptId The ID of the transcript
     * @param queryEmbeddings List of embeddings to query with
     * @param maxPromptContextLength Maximum context length for the prompt
     * @return List of relevant chunks
     */
    List<String> queryChunks(String transcriptId, List<float[]> queryEmbeddings, int maxPromptContextLength);

    /**
     * Gets the most relevant embeddings from the vector database.
     *
     * @param topK Number of top relevant embeddings to return
     * @return List of most relevant embeddings
     */
    List<float[]> getMostRelevantEmbeddings(int topK);

    /**
     * Queries chunks from all transcripts based on embeddings.
     *
     * @param queryEmbeddings List of embeddings to query with
     * @param maxPromptContextLength Maximum context length for the prompt
     * @return List of relevant chunks from all transcripts
     */
    List<String> queryAllChunks(List<float[]> queryEmbeddings, int maxPromptContextLength);

    /**
     * Result of a vector similarity search.
     */
    record VectorSearchResult(String chunk, float[] embedding, double similarityScore) {}
}