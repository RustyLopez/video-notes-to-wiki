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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsHierarchicalRollup implements EventHandler<TranscriptExecutiveSummary> {

    private static final Logger log = LoggerFactory.getLogger(EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsHierarchicalRollup.class);

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
            You are performing hierarchical summarization to create the next layer of wiki documentation (Layer {{LAYER_NUMBER_AS_2_PLUS_COMPLETED_PRIOR_ROLLUP_ITERATIONS}}).

             You will be given a set of relevant chunks from multiple related videos/recordings. Your job is to synthesize them into one cohesive Layer {{LAYER_NUMBER_AS_2_PLUS_COMPLETED_PRIOR_ROLLUP_ITERATIONS}} summary.

              Combined input length of all relevant chunks: {{TOTAL_INPUT_TOKENS_OR_WORDS}} (approximately {{APPROX_WORD_COUNT}} words).

              Produce a single Layer {{LAYER_NUMBER_AS_2_PLUS_COMPLETED_PRIOR_ROLLUP_ITERATIONS}} summary that is roughly 30% of the combined input length (target ≈ {{TARGET_WORD_COUNT}} words or fewer). Focus on maximum information density while preserving every critical insight, decision, tradeoff, action item, and unique detail.

              Relevant chunks:
              {{RELEVANT_CHUNKS}}

              First output this exact metadata header:
              **Sources Covered:** [list the Source IDs or Titles from the relevant chunks, comma-separated]
              **Layer:** {{LAYER_NUMBER_AS_2_PLUS_COMPLETED_PRIOR_ROLLUP_ITERATIONS}}
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
              - Synthesize, do not just concatenate. Eliminate all redundancy across the different relevant chunks.
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
        int targetWords = (int) Math.ceil(approxWords * llmConfig.getReductionRatio());

        String prompt = HIERARCHICAL_SUMMARIZATION_PROMPT_TEMPLATE
                .replace("{{LAYER_NUMBER_AS_2_PLUS_COMPLETED_PRIOR_ROLLUP_ITERATIONS}}", "2")
                .replace("{{TOTAL_INPUT_TOKENS_OR_WORDS}}", totalTokens + " tokens")
                .replace("{{APPROX_WORD_COUNT}}", String.valueOf(approxWords))
                .replace("{{TARGET_WORD_COUNT}}", String.valueOf(targetWords))
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