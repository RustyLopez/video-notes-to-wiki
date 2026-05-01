package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptExecutiveSummaryRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventHandlerTranscriptWithEmbeddingsToTranscriptExecutiveSummary implements EventHandler<TranscriptWithEmbeddings> {

    private final EventStream<TranscriptWithEmbeddings> eventStream;
    private final TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository;
    private final TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository;
    private final WebClient.Builder webClientBuilder;
    private final EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream;
    private final LlmConfig llmConfig;
    private final VectorStore vectorStore;


    private static final String PROMPT_TEMPLATE = """
            You are creating high-quality, professional wiki documentation.

            Here is a structured analysis and breakdown of one video: {{STRUCTURED_ANALYSIS_FROM_PROMPT_1}}

            Transform this into polished, concise wiki-ready content.

            First output this exact metadata header:
            **Source:** [Video/Recording Title or ID]
            **Length of Original:** [original duration if known]
            **Core Abstract:** [one crisp sentence capturing the single most important takeaway]

            Then produce exactly these sections (use the exact headings below):

            **Executive Summary**
            (4–6 sentences maximum, extremely high signal density, written for someone who needs to get up to speed in <60 seconds)

            **Key Insights & Takeaways**
            (comprehensive yet prioritized bullet list — aim for 8–15 bullets total)

            **Technical Concepts & Decisions**
            (explain important ideas, tradeoffs, and architecture decisions clearly — keep concise)

            **Action Items & Open Questions**
            (clearly listed with context, owners, and timelines where mentioned)

            **Topic Tags**
            (list of the 8–12 most relevant tags, comma-separated)

            **Suggested Wiki Headings**
            (list of logical section headings for a future wiki page on this video)

            Additional rules:
            - Write in clear, professional documentation tone.
            - Eliminate all redundancy.
            - Prioritize accuracy, usefulness, and merge-ability (make every bullet and sentence self-contained so it can later be combined with other summaries).
            - Total output length should feel concise and wiki-like — never verbose.
            - Do not add any extra commentary or explanations outside the requested sections.
            """;



    @PostConstruct
    public void subscribe() {
        eventStream.getEventStream()
                .flatMap(this::processEvent, llmConfig.getThreadPoolSize())
                .subscribe(
                        null,
                        error -> log.error("Error in event stream subscription", error),
                        () -> log.info("Event stream completed")
                );
        log.info("Subscribed to TranscriptWithEmbeddings event stream");
    }

    private Mono<Void> processEvent(TranscriptWithEmbeddings event) {
        return Mono.just(event)
        .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::processTranscriptWithEmbeddingsEvent)
        .doOnError(error -> log.error("Error processing event for id: {}", event.getId(), error))
        .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> processTranscriptWithEmbeddingsEvent(TranscriptWithEmbeddings transcriptWithEmbeddings) {
        log.info("Processing event for TranscriptWithEmbeddings id: {}", transcriptWithEmbeddings.getId());

        return transcriptExecutiveSummaryRepository.findById(transcriptWithEmbeddings.getId())
                .flatMap(existing -> {
                    log.warn("TranscriptExecutiveSummary already exists for id: {}, discarding event", transcriptWithEmbeddings.getId());
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> createWikiReadyTranscript(transcriptWithEmbeddings)))
                .then();
    }

    private Mono<TranscriptExecutiveSummary> createWikiReadyTranscript(TranscriptWithEmbeddings transcriptWithEmbeddings) {
        String structuredAnalysis = transcriptWithEmbeddings.getChunkEmbeddings().stream()
                .map(ce -> ce.getChunk())
                .collect(Collectors.joining(" "));
        String prompt = PROMPT_TEMPLATE.replace("{{STRUCTURED_ANALYSIS_FROM_PROMPT_1}}", structuredAnalysis);

        return callLLM(prompt)
                .flatMap(result -> {
                    // Save summary to VectorStore
                    Document summaryDoc = new Document(result, Map.of("transcriptId", transcriptWithEmbeddings.getTranscriptRawId().toString(), "type", "summary"));
                    vectorStore.add(List.of(summaryDoc));

                    // Update TranscriptWithEmbeddings
                    transcriptWithEmbeddings.setUpdatedAt(LocalDateTime.now());
                    return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings)
                            .thenReturn(result);
                })
                .flatMap(result -> {
                    TranscriptExecutiveSummary summary = new TranscriptExecutiveSummary();
                    summary.setId(UUID.randomUUID());
                    summary.setTranscriptWithEmbeddingsId(transcriptWithEmbeddings.getId());
                    summary.setResult(result);
                    summary.setCreatedAt(LocalDateTime.now());
                    summary.setUpdatedAt(LocalDateTime.now());
                    return transcriptExecutiveSummaryRepository.save(summary);
                })
                .flatMap(saved -> wikiReadyTranscriptEventStream.publish(saved).thenReturn(saved))
                .doOnNext(saved -> log.info("Saved and published TranscriptExecutiveSummary id: {} for TranscriptWithEmbeddings id: {}", saved.getId(), transcriptWithEmbeddings.getId()))
                .doOnError(error -> log.error("Error processing summary for id: {}", transcriptWithEmbeddings.getId(), error));
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

    // Optionally, for shutdown
    // @PreDestroy
    // public void unsubscribe() {
    //     if (subscription != null) {
    //         subscription.dispose();
    //     }
    // }
}