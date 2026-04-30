package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptsHierarchicalRollup;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptsHierarchicalRollupRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptExecutiveSummaryRepository;
import com.chaostensor.video_notes_to_wiki.util.TokenEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * TODO: enable if we want to revert to this hierarchical roll up strategy. It may offer additional context that the
 * current solution doesn't provide, given that we are using the "most relevant embeddings" query both in
 * {@link EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaRAG} and in the final
 * wiki summarization page with the current approach. The prompt is a bit different, but it's unlikely the rollup
 * being deployed there will produce any added value over what's already being done during wiki generation.
 *
 * This provides an alternative rollup strategy and may surface some additional key context to the wiki generate step
 * unique to this approach. So if we are going to do a rollup at all, in addition to what we already do at RAG time
 * when prompting for the wiki, then we should do something that has a chance of actually surfacing some additional
 * details of importance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaHierarchicalRollup implements EventHandler<TranscriptExecutiveSummary> {

    private final EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream;
    private final TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository;
    private final TranscriptsHierarchicalRollupRepository transcriptsHierarchicalRollupRepository;
    private final WebClient.Builder webClientBuilder;
    private final EventStream<TranscriptsHierarchicalRollup> compressedTranscriptsEventStream;
    private final LlmConfig llmConfig;
    private final TokenEstimator tokenEstimator;

    private Semaphore concurrencySemaphore;

    private static final String HIERARCHICAL_SUMMARIZATION_PROMPT_TEMPLATE = """
            You are performing hierarchical summarization to create the next layer of wiki documentation (Layer {{CURRENT_LAYER}}).
            
             You will be given a set of Layer {{PRIOR_LAYER}} executive summaries from multiple related videos/recordings. Your job is to synthesize them into one cohesive Layer {{CURRENT_LAYER}} summary.
            
             Combined input length of all Layer {{PRIOR_LAYER}}  summaries in this chunk: {{TOTAL_INPUT_TOKENS_OR_WORDS}} (approximately {{APPROX_WORD_COUNT}} words).
            
             Produce a single Layer {{CURRENT_LAYER}} summary that is roughly 30% of the combined input length (target ≈ {{TARGET_WORD_COUNT}} words or fewer). Focus on maximum information density while preserving every critical insight, decision, tradeoff, action item, and unique detail.
            
             First output this exact metadata header:
             **Sources Covered:** [list the Source IDs or Titles from the Layer {{PRIOR_LAYER}}  summaries, comma-separated]
             **Layer:** {{CURRENT_LAYER}}
             **Core Abstract:** [one crisp sentence capturing the overarching theme across all sources in this chunk]
            
             Then produce exactly these sections (use the exact headings below):
            
             **Executive Summary**
             (3–5 sentences maximum, extremely high signal density)
            
             **Key Insights & Takeaways**
             (prioritized bullet list — aggressively merge and deduplicate across all sources; aim for 6–12 bullets total)
            
             **Technical Concepts & Decisions**
             (explain important ideas, tradeoffs, and architecture decisions clearly — synthesize and consolidate)
            
             **Action Items & Open Questions**
             (clearly listed with context, owners, and timelines; merge duplicates and note any cross-video dependencies)
            
             **Topic Tags**
             (8–15 most relevant tags for the entire chunk, comma-separated)
            
             **Suggested Wiki Headings**
             (logical section headings that would work for a combined wiki page covering all sources in this chunk)
            
             Rules:
             - Synthesize, do not just concatenate. Eliminate all redundancy across the different Layer 1 summaries.
             - Preserve every unique or high-value piece of information — do not drop anything important.
             - Maintain the same professional wiki documentation tone.
             - Make every bullet and sentence self-contained and merge-ready for future layers.
             - Output ONLY the requested sections and metadata. No extra commentary.
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

        return transcriptExecutiveSummaryRepository.findAll()
                .collectList()
                .flatMap(this::chunkAndSummarizeIteratively)
                .flatMap(finalSummary -> saveAndPublishRollup(finalSummary))
                .then();
    }

    private Mono<String> chunkAndSummarizeIteratively(List<TranscriptExecutiveSummary> allSummaries) {
        if (allSummaries.isEmpty()) {
            return Mono.empty();
        }

        /*
         * NOTE the executive summaries already produced count as layer 1.
         *
         * The first iteration of this is therefore layer 2.
         */
        return Mono.fromCallable(() -> performIterativeSummarization(allSummaries, 2))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String performIterativeSummarization(List<TranscriptExecutiveSummary> summaries, int initialLayerNumber) {
        List<String> currentSummaries = summaries.stream()
                .map(TranscriptExecutiveSummary::getResult)
                .collect(Collectors.toList());

        int[] layerNumber = {initialLayerNumber};
        while (true) {
            int totalTokens = currentSummaries.stream()
                    .mapToInt(tokenEstimator::estimateTokens)
                    .sum();

            if (totalTokens <= llmConfig.getContextWindowTokens() - llmConfig.getPromptOverheadTokens()) {
                // Can process all at once
                if (currentSummaries.size() == 1) {
                    return currentSummaries.get(0);
                }
                return summarizeChunk(currentSummaries, layerNumber[0]);
            }

            // Need to chunk
            List<List<String>> chunks = createChunks(currentSummaries, llmConfig.getMaxChunkTokens());
            List<String> newSummaries = chunks.stream()
                    .map(chunk -> summarizeChunk(chunk, layerNumber[0]))
                    .collect(Collectors.toList());

            // Check if we're making progress
            int newTotalTokens = newSummaries.stream()
                    .mapToInt(tokenEstimator::estimateTokens)
                    .sum();
            double reduction = (double) totalTokens / newTotalTokens;
            if (reduction < llmConfig.getReductionRatio() * 1.1) { // Allow some tolerance
                if (newSummaries.size() == 1) {
                    throw new IllegalStateException("Cannot reduce single chunk further. Config may be invalid.");
                }
            }

            currentSummaries = newSummaries;
            layerNumber[0]++;
        }
    }

    private List<List<String>> createChunks(List<String> summaries, int maxTokensPerChunk) {
        List<List<String>> chunks = new java.util.ArrayList<>();
        List<String> currentChunk = new java.util.ArrayList<>();
        int currentTokens = 0;

        for (String summary : summaries) {
            int tokens = tokenEstimator.estimateTokens(summary);
            if (currentTokens + tokens > maxTokensPerChunk && !currentChunk.isEmpty()) {
                chunks.add(new java.util.ArrayList<>(currentChunk));
                currentChunk.clear();
                currentTokens = 0;
            }
            currentChunk.add(summary);
            currentTokens += tokens;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }

        return chunks;
    }

    private String summarizeChunk(List<String> chunk, int layerNumber) {
        String combinedInput = String.join("\n\n", chunk);
        int totalTokens = tokenEstimator.estimateTokens(combinedInput);
        int approxWords = tokenEstimator.estimateWordCount(combinedInput);
        int targetWords = (int) Math.ceil(approxWords * llmConfig.getReductionRatio());



        String prompt = HIERARCHICAL_SUMMARIZATION_PROMPT_TEMPLATE
                .replace("{{PRIOR_LAYER}}", String.valueOf(layerNumber-1))
                .replace("{{CURRENT_LAYER}}", String.valueOf(layerNumber))
                .replace("{{TOTAL_INPUT_TOKENS_OR_WORDS}}", totalTokens + " tokens")
                .replace("{{APPROX_WORD_COUNT}}", String.valueOf(approxWords))
                .replace("{{TARGET_WORD_COUNT}}", String.valueOf(targetWords))
                + "\n\nLayer 1 summaries:\n" + combinedInput;

        // Call LLM synchronously in this context
        try {
            return callLLM(prompt).block();
        } catch (Exception e) {
            log.error("Failed to summarize chunk at layer {}", layerNumber, e);
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