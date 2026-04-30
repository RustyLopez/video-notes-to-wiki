package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.service.EmbeddingService;
import com.chaostensor.video_notes_to_wiki.service.VectorDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseController {

    private final EmbeddingService embeddingService;
    private final VectorDbService vectorDbService;
    private final WebClient.Builder webClientBuilder;
    private final LlmConfig llmConfig;

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

    @PostMapping("/answer")
    public Mono<ResponseEntity<AnswerResponse>> answer(@RequestBody QueryRequest request) {
        return Mono.fromCallable(() -> List.of(request.query()))
            .flatMap(queries -> Mono.fromCallable(() -> embeddingService.embed(queries)))
            .flatMap(embeddings -> Mono.fromCallable(() -> {
                // Assume single embedding for single query
                float[] queryEmbedding = embeddings.get(0);
                // Query vector DB for chunks
                List<VectorDbService.VectorSearchResult> searchResults = vectorDbService.searchSimilar(queryEmbedding, 10); // topK=10
                // Extract chunks from results
                List<String> chunks = searchResults.stream().map(VectorDbService.VectorSearchResult::chunk).toList();
                // Join chunks as context
                String context = String.join("\n\n", chunks);
                // Create prompt
                String prompt = String.format("""
                    Answer the following question based on the provided context. If the context does not contain enough information to answer the question, say so.

                    Question: %s

                    Context:
                    %s
                    """, request.query(), context);
                // Call LLM
                return callLLM(prompt).map(answer -> ResponseEntity.ok(new AnswerResponse(answer)));
            }))
            .flatMap(mono -> mono);
    }

    private Mono<String> callLLM(String prompt) {
        WebClient webClient = webClientBuilder.baseUrl(llmConfig.getUrl()).build();
        return webClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LLMRequest.builder().prompt(prompt).build())
                .retrieve()
                .bodyToMono(LLMResponse.class)
                .map(LLMResponse::getResult)
                .onErrorResume(e -> {
                    log.error("Error calling LLM", e);
                    return Mono.error(e);
                });
    }
}