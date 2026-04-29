package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptLogicallyOrganized;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
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

@Component
public class EventHandlerTranscriptLogicallyOrganizedToTranscriptExecutiveSummary implements EventHandler<TranscriptLogicallyOrganized> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerTranscriptLogicallyOrganizedToTranscriptExecutiveSummary.class);

    private final EventStream<TranscriptLogicallyOrganized> eventStream;
    private final TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream;
    private Disposable subscription;

    private static final String PROMPT_TEMPLATE = """
            You are creating high-quality, professional wiki documentation.

            Here is a structured analysis and breakdown of one video:

            {{SIMPLIFIED_TRANSCRIPT_DATA}}

            Transform this into polished, concise wiki-ready content. Produce exactly these sections:

            **Executive Summary**
            (4-6 sentences, high signal density, written for someone who needs to get up to speed quickly)

            **Key Insights & Takeaways**
            - Comprehensive, prioritized bullet list

            **Technical Concepts & Decisions**
            - Explain important ideas, tradeoffs, and architecture decisions clearly

            **Action Items & Open Questions**
            - Clearly listed with context and any owners or timelines mentioned

            **Topic Tags**
            - List of the most relevant tags

            **Suggested Wiki Headings**
            - List of logical section headings for a wiki page on this video

            Write in clear, professional documentation tone. Eliminate redundancy. Prioritize accuracy and usefulness.
            """;

    public EventHandlerTranscriptLogicallyOrganizedToTranscriptExecutiveSummary(EventStream<TranscriptLogicallyOrganized> eventStream,
                                                                                TranscriptExecutiveSummaryRepository transcriptExecutiveSummaryRepository,
                                                                                WebClient.Builder webClientBuilder,
                                                                                ObjectMapper objectMapper,
                                                                                EventStream<TranscriptExecutiveSummary> wikiReadyTranscriptEventStream) {
        this.eventStream = eventStream;
        this.transcriptExecutiveSummaryRepository = transcriptExecutiveSummaryRepository;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build();
        this.objectMapper = objectMapper;
        this.wikiReadyTranscriptEventStream = wikiReadyTranscriptEventStream;
    }

    @PostConstruct
    public void subscribe() {
        subscription = eventStream.getEventStream()
                .flatMap(event -> processSimplifiedTranscriptEvent(event)
                        .doOnError(error -> logger.error("Error processing event for SimplifiedTranscript id: {}", event.getId(), error))
                        .onErrorResume(e -> Mono.empty()) // Continue processing other events
                )
                .subscribe(
                        null, // onNext
                        error -> logger.error("Error in event stream subscription", error),
                        () -> logger.info("Event stream completed")
                );
        logger.info("Subscribed to SimplifiedTranscript event stream");
    }

    private Mono<Void> processSimplifiedTranscriptEvent(TranscriptLogicallyOrganized transcriptLogicallyOrganized) {
        logger.info("Processing event for SimplifiedTranscript id: {}", transcriptLogicallyOrganized.getId());

        return transcriptExecutiveSummaryRepository.findById(transcriptLogicallyOrganized.getId())
                .flatMap(existing -> {
                    logger.warn("WikiReadyTranscript already exists for SimplifiedTranscript id: {}, discarding event", transcriptLogicallyOrganized.getId());
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> createWikiReadyTranscript(transcriptLogicallyOrganized)))
                .then();
    }

    private Mono<TranscriptExecutiveSummary> createWikiReadyTranscript(TranscriptLogicallyOrganized transcriptLogicallyOrganized) {
        String prompt = PROMPT_TEMPLATE.replace("{{SIMPLIFIED_TRANSCRIPT_DATA}}", transcriptLogicallyOrganized.getResult());

        return callLLM(prompt)
                .flatMap(result -> {
                    TranscriptExecutiveSummary wikiReadyTranscript = new TranscriptExecutiveSummary();
                    wikiReadyTranscript.setId(UUID.randomUUID());
                    wikiReadyTranscript.setTranscriptLogicallyOrganizedId(transcriptLogicallyOrganized.getId());
                    wikiReadyTranscript.setResult(result);
                    wikiReadyTranscript.setCreatedAt(LocalDateTime.now());
                    wikiReadyTranscript.setUpdatedAt(LocalDateTime.now());
                    return transcriptExecutiveSummaryRepository.save(wikiReadyTranscript);
                })
                .flatMap(saved -> wikiReadyTranscriptEventStream.publish(saved).thenReturn(saved))
                .doOnNext(saved -> logger.info("Saved and published WikiReadyTranscript id: {} for SimplifiedTranscript id: {}", saved.getId(), transcriptLogicallyOrganized.getId()))
                .doOnError(error -> {
                    logger.error("Error processing WikiReadyTranscript for SimplifiedTranscript id: {}", transcriptLogicallyOrganized.getId(), error);
                    // Log stack trace
                    for (StackTraceElement element : error.getStackTrace()) {
                        logger.error(element.toString());
                    }
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
                    logger.error("Error calling LLM", e);
                    for (StackTraceElement element : e.getStackTrace()) {
                        logger.error(element.toString());
                    }
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