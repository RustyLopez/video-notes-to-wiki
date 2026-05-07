package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptsHierarchicalRollup;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptExecutiveSummaryRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptsHierarchicalRollupRepository;
import com.chaostensor.video_notes_to_wiki.service.EmbeddingService;
import com.chaostensor.video_notes_to_wiki.util.TokenEstimator;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaHierarchicalRollup implements EventHandler<TranscriptExecutiveSummary> {

    private final EmbeddingService embeddingService;
    private final EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream;
    private final TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository;
    private final TranscriptsHierarchicalRollupRepository transcriptsHierarchicalRollupRepository;
    private final WebClient.Builder webClientBuilder;
    private final EventStream<TranscriptsHierarchicalRollup> compressedTranscriptsEventStream;
    private final LlmConfig llmConfig;
    private final TokenEstimator tokenEstimator;
    private final VectorStore vectorStore;

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

    private Mono<Void> processEvent(final TranscriptExecutiveSummary event) {
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

    private Mono<Void> performHierarchicalRollup(final TranscriptExecutiveSummary triggerEvent) {
        log.info("Starting hierarchical rollup triggered by: {}", triggerEvent.getId());

        return transcriptExecutiveSummaryRepository.findAll()
                .collectList()
                .flatMap(this::chunkAndSummarizeIteratively)
                .flatMap(finalSummary -> saveAndPublishRollup(finalSummary))

                .then();
    }


    private Mono<String> chunkAndSummarizeIteratively(final List<TranscriptExecutiveSummary> allSummaries) {
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

    private String performIterativeSummarization(final List<TranscriptExecutiveSummary> summaries, final int initialLayerNumber) {
        List<String> currentSummaries = summaries.stream()
                .map(TranscriptExecutiveSummary::getResult)
                .collect(Collectors.toList());

        final int[] layerNumber = {initialLayerNumber};
        while (true) {
            final int totalTokens = currentSummaries.stream()
                    .mapToInt(tokenEstimator::estimateTokens)
                    .sum();

            if (totalTokens <= llmConfig.getContextWindowTokens() - llmConfig.getPromptOverheadTokens()) {
                // Can process all at once
                if (currentSummaries.size() == 1) {
                    return currentSummaries.get(0);
                }
                return summarizeChunk(currentSummaries, layerNumber[0]).block();
            }

            // Need to chunk
            final ImmutableList<ImmutableList<String>> chunks = createChunks(currentSummaries, llmConfig.getMaxChunkTokens());
            final List<Mono<String>> newSummaries = chunks.stream()
                    .map(chunk -> summarizeChunk(chunk, layerNumber[0]))
                    .collect(Collectors.toList());

            final List<String> newSummariesStrings = Flux.fromIterable(newSummaries).flatMap(Function.identity()).collectList().block();

            // Check if we're making progress
            final int newTotalTokens = newSummariesStrings.stream()
                    .mapToInt(tokenEstimator::estimateTokens)
                    .sum();
            final double reduction = (double) totalTokens / newTotalTokens;
            if (reduction < llmConfig.getHierarchicalSummaryStrategyConfigsPerLayerReductionRatio() * 1.1/* TODO no idea why this random arbitrary tolerance got added. If anything we want to erro ont he side of being LESS tolerant */) { // Allow some tolerance
                if (newSummariesStrings.size() == 1) {
                    throw new IllegalStateException("Cannot reduce single chunk further. Config may be invalid.");
                }
            }

            currentSummaries = newSummariesStrings;
            layerNumber[0]++;
        }
    }

    private ImmutableList<ImmutableList<String>> createChunks(final List<String> summaries, final int maxTokensPerChunk) {
        final ImmutableList.Builder<ImmutableList<String>> chunks = ImmutableList.builder();
        final List<String> currentChunk = new java.util.ArrayList<>();
        int currentTokens = 0;

        for (final String summary : summaries) {
            final int tokens = tokenEstimator.estimateTokens(summary);
            if (currentTokens + tokens > maxTokensPerChunk && !currentChunk.isEmpty()) {
                chunks.add(ImmutableList.copyOf(currentChunk));
                currentChunk.clear();
                currentTokens = 0;
            }
            currentChunk.add(summary);
            currentTokens += tokens;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(ImmutableList.copyOf(currentChunk));
        }

        return chunks.build();
    }

    private Mono<String> summarizeChunk(final List<String> chunk, final int layerNumber) {
        final String combinedInput = String.join("\n\n", chunk);
        final int totalTokens = tokenEstimator.estimateTokens(combinedInput);
        final int approxWords = tokenEstimator.estimateWordCount(combinedInput);
        final int targetWords = (int) Math.ceil(approxWords * llmConfig.getHierarchicalSummaryStrategyConfigsPerLayerReductionRatio());


        final String prompt = HIERARCHICAL_SUMMARIZATION_PROMPT_TEMPLATE
                .replace("{{PRIOR_LAYER}}", String.valueOf(layerNumber - 1))
                .replace("{{CURRENT_LAYER}}", String.valueOf(layerNumber))
                .replace("{{TOTAL_INPUT_TOKENS_OR_WORDS}}", totalTokens + " tokens")
                .replace("{{APPROX_WORD_COUNT}}", String.valueOf(approxWords))
                .replace("{{TARGET_WORD_COUNT}}", String.valueOf(targetWords))
                + "\n\nLayer 1 summaries:\n" + combinedInput;

        return callLLM(prompt)
                .onErrorResume(e -> {
                    log.error("Failed to summarize chunk at layer {}", layerNumber, e);
                    return Mono.error(new RuntimeException("LLM summarization failed", e));
                });
    }

    private Mono<String> callLLM(final String prompt) {
        final WebClient webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build();
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

    private Mono<TranscriptsHierarchicalRollup> saveAndPublishRollup(final String summary) {

        return chunkOutputAndGenerateEmbeddings(summary).flatMap(chunkEmbeddings -> {
            final TranscriptsHierarchicalRollup rollup = new TranscriptsHierarchicalRollup();
            rollup.setCompressedResult(summary);
            rollup.setCreatedAt(LocalDateTime.now());
            rollup.setUpdatedAt(LocalDateTime.now());
            rollup.setChunksWithEmbeddings(chunkEmbeddings);

            return transcriptsHierarchicalRollupRepository.save(rollup)
                    .flatMap(saved -> compressedTranscriptsEventStream.publish(saved).thenReturn(saved))
                    /**
                     * NOTE saving ot a separte  datastore here so we need to... yeah
                     * make stuff ideomtponetn avnd able to gete eventuallyc onsistent
                     *
                     * shodul be fine if we fail ehere the event consume shoudl fail
                     *
                     * fi the evnet ocnsuem failes ( well actualy confirm that on the sink.. )
                     *
                     *  then we re-process the event, we may end up overwriting the value in either
                     *  of the two stores on a retry if one manage sto save.. should be fine.
                     */
                    .flatMap(saved -> saveAlsoToVectorDbWithEmbeddings(saved).thenReturn(saved))
                    .doOnNext(saved -> log.info("Saved and published hierarchical rollup id: {}", saved.getId()));

        });

    }

    private Mono<TranscriptsHierarchicalRollup> saveAlsoToVectorDbWithEmbeddings(final TranscriptsHierarchicalRollup saved) {
        final List<Document> documents = saved.getChunksWithEmbeddings().stream()
                .map(ce -> new Document(ce.getChunk(), Map.of("transcriptId", saved.getId().toString(), "type", "hierarchical")))
                .toList();
        vectorStore.add(documents);
        return Mono.just(saved);
    }

    private Mono<ImmutableList<TranscriptWithEmbeddings.ChunkEmbedding>> chunkOutputAndGenerateEmbeddings(final String summary) {
        return Mono.fromCallable(() -> chunkByBulletPointsSectionHeadersAndDoubleNewlines(summary))
                .flatMap(chunks -> {
                    if (chunks.isEmpty()) {
                        return Mono.empty();
                    }

                    return Flux.zip(
                                    Flux.fromIterable(chunks),
                                    Mono.fromCallable(() -> embeddingService.embed(chunks))
                                            .flatMapMany(Flux::fromIterable)
                            )
                            .map(tuple -> {
                                return new TranscriptWithEmbeddings.ChunkEmbedding(tuple.getT1(), tuple.getT2());
                            })
                            .collectList()
                            .map(ImmutableList::copyOf);
                });
    }

    /**
     * TODO decie a bette mrethod
     *
     * @param summary
     * @return
     */
    public static ImmutableList<String> chunkByBulletPointsSectionHeadersAndDoubleNewlines(final String summary) {

        final ImmutableList.Builder<String> chunks = ImmutableList.builder();
        // Simple regex to split by level 1 headings (# )
        // TODO confirm the U+2022 char is correctly here for the regex. right there, test teh
        // regex in generaly
        // may need..  yeah..
        final Pattern pattern = Pattern.compile("(#+|\\n\\n+|\\*+|\\u2022)");
        final Matcher matcher = pattern.matcher(summary);
        int lastEnd = 0;
        while (matcher.find()) {
            if (lastEnd > 0) {
                final String chunk = summary.substring(lastEnd, matcher.start()).trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
            }
            lastEnd = matcher.start();
        }
        // Add the last chunk
        if (lastEnd < summary.length()) {
            final String chunk = summary.substring(lastEnd).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }
        return chunks.build();
    }
}