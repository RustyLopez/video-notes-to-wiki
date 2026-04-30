package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.service.EmbeddingService;
import com.chaostensor.video_notes_to_wiki.service.VectorDbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {

    private final EmbeddingService embeddingService;
    private final VectorDbService vectorDbService;

    public KnowledgeBaseController(EmbeddingService embeddingService, VectorDbService vectorDbService) {
        this.embeddingService = embeddingService;
        this.vectorDbService = vectorDbService;
    }

    @PostMapping("/query")
    public Mono<ResponseEntity<QueryResponse>> query(@RequestBody QueryRequest request) {
        return Mono.fromCallable(() -> List.of(request.query()))
            .flatMap(queries -> Mono.fromCallable(() -> embeddingService.embed(queries)))
            .flatMap(embeddings -> Mono.fromCallable(() -> {
                // Assume single embedding for single query
                float[] queryEmbedding = embeddings.get(0);
                // Query vector DB for chunks
                List<VectorDbService.VectorSearchResult> searchResults = vectorDbService.searchSimilar(queryEmbedding, 10); // topK=10
                // Extract chunks from results
                List<String> chunks = searchResults.stream().map(VectorDbService.VectorSearchResult::chunk).toList();
                // Resolve chunks to IDs
                VectorDbService.ResolvedIds resolvedIds = vectorDbService.resolveChunksToIds(chunks);
                // Return response
                return ResponseEntity.ok(new QueryResponse(
                    resolvedIds.transcriptRawIds(),
                    resolvedIds.transcriptExecutiveSummaryIds(),
                    resolvedIds.transcriptsHierarchicalRollupIds()
                ));
            }));
    }
}