package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptsHierarchicalRollup;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptsHierarchicalRollupRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptExecutiveSummaryRepository;
import com.chaostensor.video_notes_to_wiki.service.VectorDbService;
import com.chaostensor.video_notes_to_wiki.util.TokenEstimator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * See the docs in {@link EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaHierarchicalRollup}. The problem with this approach is that, while simpler, it is also a
 * lot more redundant to the strategy already used in the final wiki generate prompt step
 * {@link EventHandlerTranscriptsHierarchicalRollupToWiki}. Which will invariably result in a lot of overlap and
 * duplicated context being passed in to that request.  Therefore, if we are going to have an additional strategy like this
 * to try and surface key aspects of the knowledge base for wiki inclusion, it should probably be using an actually
 * distinct strategy for identifying that information. A hierarchical rollup is a valid alternative to a RAG for this
 * purpose. And therefore the solution found in {@link EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaHierarchicalRollup} likely offers more value than this one
 * for attempting to get a second perspective on knowledge base information relevance.
 *
 * TODO: I'm retaining this for now. Whic his ab it of an anti-pattern. You should avoid retaining unused code.  But
 * i'm not yet 100% certain that I want to move forward with the other approach.
 */
//@Component
@RequiredArgsConstructor
public class EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaRAG implements EventHandler<TranscriptExecutiveSummary> {

    private static final Logger log = LoggerFactory.getLogger(EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaRAG.class);
    public static final int PERCENT_SCALE = 2;

    private final EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream;
    private final TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository;
    private final TranscriptsHierarchicalRollupRepository transcriptsHierarchicalRollupRepository;
    private final WebClient.Builder webClientBuilder;
    private final EventStream<TranscriptsHierarchicalRollup> compressedTranscriptsEventStream;
    private final LlmConfig llmConfig;
    private final TokenEstimator tokenEstimator;
    private final VectorDbService vectorDbService;

    private Semaphore concurrencySemaphore;

    private static final String HIERARCHICAL_SUMMARIZATION_PROMPT_TEMPLATE = """
            You are an expert knowledge architect building a comprehensive internal wiki from a series of videos.
            
             Additional relevant chunks from the vector database:
            
             {{RELEVANT_CHUNKS}}
            
             Synthesize all of this into a coherent, hierarchical knowledge base. Produce:
             1. **Master Executive Summary** — one strong paragraph covering the entire series
             2. **Hierarchical Topic Structure** — organize the content into logical parent topics and subtopics (use markdown headings)
             3. **Cross-Video Insights & Connections** — highlight how ideas from different videos relate, reinforce each other, or contradict
             4. **Consolidated Action Item Tracker** — all action items with video references
             5. **Recommended Wiki Structure** — suggest actual wiki pages with titles and outline of sections for each page
             6. **Knowledge Gaps or Follow-up Topics** (if any)
             Focus on creating something a new engineer could read and rapidly understand the key decisions, architecture, and current state of the project. Remove duplication across videos. Create clean hierarchy.
            
             Produce a summary that is roughly {{TARGET_NEXT_PROMPT_OCCUPY_PERCENT}}% of the maximum context length for a followup prompt (target ≈ {{TARGET_WORD_COUNT}} words or fewer). Focus on maximum information density while preserving every critical insight, decision, tradeoff, action item, and unique detail.
            """;

    @PostConstruct
    public void init() {
        this.concurrencySemaphore = new Semaphore(llmConfig.getThreadPoolSize());
    }

    @PostConstruct
    public void subscribe() {
        wikiReadyTranscriptEventStream.getEventStream()
                .flatMap(this::processEvent, llmConfig.getThreadPoolSize())
                .subscribe(
                    null,
                    error -> log.error("Error in event stream subscription", error),
                    () -> log.info("Event stream completed")
                );
        log.info("Subscribed to TranscriptExecutiveSummary event stream");
    }

    private Mono<Void> processEvent(TranscriptExecutiveSummary event) {
        return Mono.fromCallable(() -> {
            concurrencySemaphore.acquire();
            return event;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(this::performHierarchicalRollup)
        .doOnError(error -> log.error("Error processing rollup for id: {}", event.getId(), error))
        .onErrorResume(e -> Mono.empty())
        .doFinally(signalType -> concurrencySemaphore.release());
    }

    private Mono<Void> performHierarchicalRollup(TranscriptExecutiveSummary triggerEvent) {
        log.info("Starting hierarchical rollup triggered by: {}", triggerEvent.getId());

        return Mono.fromCallable(() -> vectorDbService.getMostRelevantEmbeddings(100)) // Placeholder topK
                .subscribeOn(Schedulers.boundedElastic())
                .map(relevantEmbeddings ->
                    vectorDbService.queryAllChunks(relevantEmbeddings, llmConfig.getContextWindowTokens() - llmConfig.getPromptOverheadTokens())
                )
                .flatMap(relevantChunks -> Mono.fromCallable(() -> summarizeRelevantChunks(relevantChunks)))
                .flatMap(finalSummary -> saveAndPublishRollup(finalSummary))
                .then();
    }

    private String summarizeRelevantChunks(List<String> relevantChunks) {
        String combinedChunks = String.join("\n\n", relevantChunks);
        int totalTokens = tokenEstimator.estimateTokens(combinedChunks);
        int approxWords = tokenEstimator.estimateWordCount(combinedChunks);
        int targetWords = (int) Math.ceil(approxWords * llmConfig.getHierarchicalSummaryStrategyConfigsPerLayerReductionRatio());

        String ragProducedMasterExecutiveSummaryMaxContextOccupationPercent = BigDecimal.valueOf(llmConfig.getRagProducedMasterExecutiveSummaryMaxContextOccupationRatio())
                .multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.FLOOR)
                .toString();
        int targetWordCount = BigDecimal.valueOf((float)tokenEstimator.estimateMaxWordCountForMaxTokens(llmConfig.getMaxChunkTokens()) * llmConfig.getRagProducedMasterExecutiveSummaryMaxContextOccupationRatio())
                .setScale(0, RoundingMode.FLOOR)
                .intValue();

        String prompt = HIERARCHICAL_SUMMARIZATION_PROMPT_TEMPLATE
                .replace("{{TARGET_NEXT_PROMPT_OCCUPY_PERCENT}}", ragProducedMasterExecutiveSummaryMaxContextOccupationPercent)
                .replace("{{TARGET_WORD_COUNT}}", String.valueOf(targetWordCount))
                .replace("{{RELEVANT_CHUNKS}}", combinedChunks);

        // Call LLM synchronously in this context
        try {
            return callLLM(prompt).block();
        } catch (Exception e) {
            log.error("Failed to summarize relevant chunks", e);
            throw new RuntimeException("LLM summarization failed", e);
        }
    }

    private Mono<String> callLLM(String prompt) {
        WebClient webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build();
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

    private Mono<TranscriptsHierarchicalRollup> saveAndPublishRollup(String summary) {
        TranscriptsHierarchicalRollup rollup = new TranscriptsHierarchicalRollup();
        rollup.setId(UUID.randomUUID());
        rollup.setCompressedResult(summary);
        rollup.setCreatedAt(LocalDateTime.now());
        rollup.setUpdatedAt(LocalDateTime.now());

        return transcriptsHierarchicalRollupRepository.save(rollup)
                .flatMap(saved -> compressedTranscriptsEventStream.publish(saved).thenReturn(saved))
                .doOnNext(saved -> log.info("Saved and published hierarchical rollup id: {}", saved.getId()));
    }
}