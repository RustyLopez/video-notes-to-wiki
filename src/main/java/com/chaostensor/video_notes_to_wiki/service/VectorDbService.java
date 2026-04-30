package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import reactor.core.publisher.Mono;

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
    Mono<List<TranscriptWithEmbeddings.ChunkEmbedding>> saveChunkEmbeddings(String transcriptId, List<TranscriptWithEmbeddings.ChunkEmbedding> chunkEmbeddings);

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
     *   Possible?
     *
     *   I changed the impl a bit so this is not technically needed at the moment.
     *   But I can foresee future possible use cases for getting a quick list of themes or topics
     *   most saturated in the knowledge base.
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

    /**
     * Resolves a list of chunks to their corresponding document IDs.
     * Placeholder implementation that assumes IDs can be derived from chunk data.
     *
     * @param chunks List of chunk strings
     * @return ResolvedIds containing lists of IDs for each document type
     */
    ResolvedIds resolveChunksToIds(List<String> chunks);

    /**
     * Resolved IDs for different document types.
     */
    record ResolvedIds(
        List<UUID> transcriptRawIds,
        List<UUID> transcriptExecutiveSummaryIds,
        List<UUID> transcriptsHierarchicalRollupIds
    ) {}
}