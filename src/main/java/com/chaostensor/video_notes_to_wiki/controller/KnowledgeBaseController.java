package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
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
    public Mono<ResponseEntity<QueryResponse>> query(@RequestBody QueryRequest request) {
        return Mono.fromCallable(() -> {
            // Query vector DB for chunks
            SearchRequest searchRequest = SearchRequest.builder().query(request.query()).topK(10).build();
            List<Document> searchResults = vectorStore.similaritySearch(searchRequest);
            // Extract chunks from results
            List<String> chunks = searchResults.stream().map(Document::getText).toList();
            // Resolve documents to IDs
            ResolvedIds resolvedIds = resolveDocumentsToIds(searchResults);
            // Return response
            return ResponseEntity.ok(new QueryResponse(
                resolvedIds.transcriptRawIds(),
                resolvedIds.transcriptExecutiveSummaryIds(),
                resolvedIds.transcriptsHierarchicalRollupIds()
            ));
        });
    }

    @PostMapping("/answer")
    public Mono<ResponseEntity<AnswerResponse>> answer(@RequestBody QueryRequest request) {
        return Mono.fromCallable(() -> {
            // Query vector DB for chunks
            SearchRequest searchRequest = SearchRequest.builder().query(request.query()).topK(10).build();
            List<Document> searchResults = vectorStore.similaritySearch(searchRequest);
            // Extract chunks from results
            List<String> chunks = searchResults.stream().map(Document::getText).toList();
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
        })
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

    private ResolvedIds resolveDocumentsToIds(List<Document> documents) {
        List<UUID> transcriptRawIds = new ArrayList<>();
        List<UUID> transcriptExecutiveSummaryIds = new ArrayList<>();
        List<UUID> transcriptsHierarchicalRollupIds = new ArrayList<>();

        for (Document doc : documents) {
            Map<String, Object> metadata = doc.getMetadata();
            String type = (String) metadata.get("type");
            String transcriptIdStr = (String) metadata.get("transcriptId");
            if (transcriptIdStr != null) {
                UUID transcriptId = UUID.fromString(transcriptIdStr);
                switch (type) {
                    case "chunk" -> transcriptRawIds.add(transcriptId);
                    case "summary" -> transcriptExecutiveSummaryIds.add(transcriptId);
                    case "hierarchical" -> transcriptsHierarchicalRollupIds.add(transcriptId);
                }
            }
        }
        return new ResolvedIds(transcriptRawIds, transcriptExecutiveSummaryIds, transcriptsHierarchicalRollupIds);
    }

    public record ResolvedIds(
        List<UUID> transcriptRawIds,
        List<UUID> transcriptExecutiveSummaryIds,
        List<UUID> transcriptsHierarchicalRollupIds
    ) {}
}