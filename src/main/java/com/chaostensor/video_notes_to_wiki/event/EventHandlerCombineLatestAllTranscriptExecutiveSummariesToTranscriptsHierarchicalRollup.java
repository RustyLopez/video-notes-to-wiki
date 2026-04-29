package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptsHierarchicalRollup;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptsHierarchicalRollupRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptExecutiveSummaryRepository;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsHierarchicalRollup implements EventHandler<TranscriptExecutiveSummary> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsHierarchicalRollup.class);

    private final EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream;
    private final TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository;
    private final TranscriptsHierarchicalRollupRepository transcriptsHierarchicalRollupRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EventStream<TranscriptsHierarchicalRollup> compressedTranscriptsEventStream;
    private Disposable subscription;

    private static final String COMPRESSION_PROMPT_TEMPLATE = """
            Compress the following collection of wiki-ready transcripts into a condensed summary that retains all key information, insights, action items, and topic tags. Reduce the total length to fit within a single LLM context window while preserving the most important details and structure.

            Wiki-ready transcripts:

            {{ALL_WIKI_READY_TRANSCRIPTS}}

            Provide the compressed summary in a structured format.
            """;

    public EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsHierarchicalRollup(EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream,
                                                                                                    TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository,
                                                                                                    TranscriptsHierarchicalRollupRepository transcriptsHierarchicalRollupRepository,
                                                                                                    WebClient.Builder webClientBuilder,
                                                                                                    ObjectMapper objectMapper,
                                                                                                    EventStream<TranscriptsHierarchicalRollup> compressedTranscriptsEventStream) {
        this.wikiReadyTranscriptEventStream = wikiReadyTranscriptEventStream;
        this.transcriptExecutiveSummaryRepository = transcriptExecutiveSummaryRepository;
        this.transcriptsHierarchicalRollupRepository = transcriptsHierarchicalRollupRepository;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build();
        this.objectMapper = objectMapper;
        this.compressedTranscriptsEventStream = compressedTranscriptsEventStream;
    }

    @PostConstruct
    public void subscribe() {
        subscription = wikiReadyTranscriptEventStream.getEventStream()
            .flatMap(event -> processWikiReadyTranscriptEvent(event)
                .doOnError(error -> logger.error("Error processing event for TranscriptExecutiveSummary id: {}", event.getId(), error))
                .onErrorResume(e -> Mono.empty()) // Continue processing other events
            )
            .subscribe(
                null, // onNext
                error -> logger.error("Error in event stream subscription", error),
                () -> logger.info("Event stream completed")
            );
        logger.info("Subscribed to TranscriptExecutiveSummary event stream");
    }

    private Mono<Void> processWikiReadyTranscriptEvent(TranscriptExecutiveSummary transcriptExecutiveSummary) {
        logger.info("Processing compression for WikiReadyTranscript id: {}", transcriptExecutiveSummary.getId());

        return transcriptExecutiveSummaryRepository.findAll()
                .collectList()
                .flatMap(allTranscripts -> {
                    if (allTranscripts.isEmpty()) {
                        logger.warn("No WikiReadyTranscripts found");
                        return Mono.empty();
                    } else {
                        String allTranscriptsText = allTranscripts.stream()
                                .map(TranscriptExecutiveSummary::getResult)
                                .collect(Collectors.joining("\n\n"));
                        String prompt = COMPRESSION_PROMPT_TEMPLATE.replace("{{ALL_WIKI_READY_TRANSCRIPTS}}", allTranscriptsText);
                        return callLLM(prompt)
                                .flatMap(compressedResult -> {
                                    TranscriptsHierarchicalRollup compressed = new TranscriptsHierarchicalRollup();
                                    compressed.setId(UUID.randomUUID());
                                    compressed.setCompressedResult(compressedResult);
                                    compressed.setCreatedAt(LocalDateTime.now());
                                    compressed.setUpdatedAt(LocalDateTime.now());
                                    return transcriptsHierarchicalRollupRepository.save(compressed);
                                })
                                .flatMap(saved -> compressedTranscriptsEventStream.publish(saved).thenReturn(saved))
                                .doOnNext(saved -> logger.info("Saved and published CompressedTranscripts id: {}", saved.getId()))
                                .then();
                    }
                })
                .onErrorResume(e -> {
                    logger.error("Error processing compression for WikiReadyTranscript id: {}", transcriptExecutiveSummary.getId(), e);
                    return Mono.empty();
                });
    }

    private Mono<String> callLLM(String prompt) {
        return webClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LLMRequest.builder().prompt(prompt).build())
                .retrieve()
                .bodyToMono(LLMResponse.class)
                .map(LLMResponse::getResult)
                .onErrorResume(e -> {
                    logger.error("Error calling LLM for compression", e);
                    return Mono.error(e);
                });
    }

    // Optionally, for shutdown
    // @PreDestroy
    // public void unsubscribe() {
    //     if (subscription != null) {
    //         subscription.dispose();
    //     }
    // }
}