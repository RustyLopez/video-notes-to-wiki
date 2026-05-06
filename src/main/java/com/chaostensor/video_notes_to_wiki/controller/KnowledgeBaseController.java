package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.Builder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseController {

    private final VectorStore vectorStore;
    private final WebClient.Builder webClientBuilder;
    private final LlmConfig llmConfig;

    @PostMapping("/query")
    public Mono<ResponseEntity<QueryResponse>> query(@RequestBody final QueryRequest request) {
        return Mono.fromCallable(() -> {
            // Query vector DB for chunks
            final SearchRequest searchRequest = SearchRequest.builder().query(request.getQuery()).topK(10).build();
            final List<Document> searchResults = vectorStore.similaritySearch(searchRequest);
            // Extract chunks from results
            final List<String> chunks = searchResults.stream().map(Document::getText).toList();
            // Resolve documents to IDs
            final ResolvedIds resolvedIds = resolveDocumentsToIds(searchResults);
            // Return response
            return ResponseEntity.ok(new QueryResponse(
                resolvedIds.getTranscriptRawIds(),
                resolvedIds.getTranscriptExecutiveSummaryIds(),
                resolvedIds.getTranscriptsHierarchicalRollupIds()
            ));
        });
    }

    @PostMapping("/answer")
    public Mono<ResponseEntity<AnswerResponse>> answer(@RequestBody final QueryRequest request) {
        return Mono.fromCallable(() -> {
            // Query vector DB for chunks
            final SearchRequest searchRequest = SearchRequest.builder().query(request.getQuery()).topK(10).build();
            final List<Document> searchResults = vectorStore.similaritySearch(searchRequest);
            // Extract chunks from results
            final List<String> chunks = searchResults.stream().map(Document::getText).toList();
            // Join chunks as context
            final String context = String.join("\n\n", chunks);
            // Create prompt
            final String prompt = String.format("""
                Answer the following question based on the provided context. If the context does not contain enough information to answer the question, say so.

                Question: %s

                Context:
                %s
                """, request.getQuery(), context);
            // Call LLM
            return callLLM(prompt).map(answer -> ResponseEntity.ok(new AnswerResponse(answer)));
        })
        .flatMap(mono -> mono);
    }

    private Mono<String> callLLM(final String prompt) {
        final WebClient webClient = webClientBuilder.baseUrl(llmConfig.getUrl()).build();
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

    private ResolvedIds resolveDocumentsToIds(final List<Document> documents) {
        final ImmutableList.Builder<UUID> transcriptRawIdsBuilder = ImmutableList.builder();
        final ImmutableList.Builder<UUID> transcriptExecutiveSummaryIdsBuilder = ImmutableList.builder();
        final ImmutableList.Builder<UUID> transcriptsHierarchicalRollupIdsBuilder = ImmutableList.builder();

        for (final Document doc : documents) {
            final Map<String, Object> metadata = doc.getMetadata();
            final String type = (String) metadata.get("type");
            final String transcriptIdStr = (String) metadata.get("transcriptId");
            if (transcriptIdStr != null) {
                final UUID transcriptId = UUID.fromString(transcriptIdStr);
                switch (type) {
                    case "chunk" -> transcriptRawIdsBuilder.add(transcriptId);
                    case "summary" -> transcriptExecutiveSummaryIdsBuilder.add(transcriptId);
                    case "hierarchical" -> transcriptsHierarchicalRollupIdsBuilder.add(transcriptId);
                }
            }
        }
        return new ResolvedIds(transcriptRawIdsBuilder.build(), transcriptExecutiveSummaryIdsBuilder.build(), transcriptsHierarchicalRollupIdsBuilder.build());
    }

    @Value
    @Builder
    public static class ResolvedIds {
        private final ImmutableList<UUID> transcriptRawIds;
        private final ImmutableList<UUID> transcriptExecutiveSummaryIds;
        private final ImmutableList<UUID> transcriptsHierarchicalRollupIds;
    }
}