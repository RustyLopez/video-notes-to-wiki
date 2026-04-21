package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import com.chaostensor.video_notes_to_wiki.entity.WikiReadyTranscript;
import com.chaostensor.video_notes_to_wiki.entity.WikiReadyTranscriptCompletionGroup;
import com.chaostensor.video_notes_to_wiki.entity.WikiResult;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.SimplifiedTranscriptRepository;
import com.chaostensor.video_notes_to_wiki.repository.WikiReadyTranscriptRepository;
import com.chaostensor.video_notes_to_wiki.repository.WikiResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

import java.util.stream.Collectors;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class WikiCompletionSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(WikiCompletionSubscriber.class);

    private final WikiReadyTranscriptCompletionGroupEventPublisher completionGroupEventPublisher;
    private final SimplifiedTranscriptRepository simplifiedTranscriptRepository;
    private final WikiReadyTranscriptRepository wikiReadyTranscriptRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final WikiResultRepository wikiResultRepository;
    private final WikiResultEventPublisher wikiResultEventPublisher;
    private Disposable subscription;

    private static final String SYNTHESIS_PROMPT_TEMPLATE = """
        You are an expert knowledge architect building a comprehensive internal wiki from a series of videos.

        Here is the polished documentation synthesized from each individual video:

        {{ALL_WIKI_READY_TRANSCRIPTS_FOR_A_GIVEN_JOB}}

        Synthesize all of this into a coherent, hierarchical knowledge base. Produce:

        1. **Master Executive Summary** — one strong paragraph covering the entire series
        2. **Hierarchical Topic Structure** — organize the content into logical parent topics and subtopics (use markdown headings)
        3. **Cross-Video Insights & Connections** — highlight how ideas from different videos relate, reinforce each other, or contradict
        4. **Consolidated Action Item Tracker** — all action items with video references
        5. **Recommended Wiki Structure** — suggest actual wiki pages with titles and outline of sections for each page
        6. **Knowledge Gaps or Follow-up Topics** (if any)

        Focus on creating something a new engineer could read and rapidly understand the key decisions, architecture, and current state of the project. Remove duplication across videos. Create clean hierarchy.
        """;

    public WikiCompletionSubscriber(WikiReadyTranscriptCompletionGroupEventPublisher completionGroupEventPublisher,
                                    SimplifiedTranscriptRepository simplifiedTranscriptRepository,
                                    WikiReadyTranscriptRepository wikiReadyTranscriptRepository,
                                    WebClient.Builder webClientBuilder,
                                    ObjectMapper objectMapper,
                                    WikiResultRepository wikiResultRepository,
                                    WikiResultEventPublisher wikiResultEventPublisher) {
        this.completionGroupEventPublisher = completionGroupEventPublisher;
        this.simplifiedTranscriptRepository = simplifiedTranscriptRepository;
        this.wikiReadyTranscriptRepository = wikiReadyTranscriptRepository;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build();
        this.objectMapper = objectMapper;
        this.wikiResultRepository = wikiResultRepository;
        this.wikiResultEventPublisher = wikiResultEventPublisher;
    }

    @PostConstruct
    public void subscribe() {
        subscription = completionGroupEventPublisher.getEventStream()
            .flatMap(this::processCompletionGroupEvent)
            .subscribe(
                null, // onNext
                error -> logger.error("Error in WikiReadyTranscriptCompletionGroup event stream subscription", error),
                () -> logger.info("WikiReadyTranscriptCompletionGroup event stream completed")
            );
        logger.info("Subscribed to WikiReadyTranscriptCompletionGroup event stream");
    }

    private Mono<Void> processCompletionGroupEvent(WikiReadyTranscriptCompletionGroup completionGroup) {
        UUID jobId = completionGroup.getJobId();
        // Find all SimplifiedTranscripts for the job, then get their WikiReadyTranscripts
        return simplifiedTranscriptRepository.findByJobId(jobId)
            .flatMap(simplified -> wikiReadyTranscriptRepository.findBySimplifiedTranscriptId(simplified.getId()))
            .collectList()
            .flatMap(wikiTranscripts -> {
                if (wikiTranscripts.isEmpty()) {
                    logger.warn("No WikiReadyTranscripts found for jobId {}", jobId);
                    return Mono.empty();
                } else {
                    String allTranscripts = wikiTranscripts.stream()
                        .map(WikiReadyTranscript::getResult)
                        .collect(Collectors.joining("\n\n"));
                    String prompt = SYNTHESIS_PROMPT_TEMPLATE.replace("{{ALL_WIKI_READY_TRANSCRIPTS_FOR_A_GIVEN_JOB}}", allTranscripts);
                    return callLLM(prompt)
                        .flatMap(result -> {
                            WikiResult wikiResult = new WikiResult();
                            wikiResult.setId(UUID.randomUUID());
                            wikiResult.setJobId(jobId);
                            wikiResult.setResult(result);
                            wikiResult.setCreatedAt(LocalDateTime.now());
                            wikiResult.setUpdatedAt(LocalDateTime.now());
                            return wikiResultRepository.save(wikiResult);
                        })
                        .flatMap(saved -> wikiResultEventPublisher.publish(saved).thenReturn(saved))
                        .doOnNext(saved -> logger.info("Saved and published WikiResult id: {} for jobId: {}", saved.getId(), jobId))
                        .then();
                }
            })
            .onErrorResume(e -> {
                logger.error("Error processing WikiReadyTranscriptCompletionGroup event", e);
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
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
                logger.error("Error calling LLM for synthesis", e);
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
                return Mono.error(e);
            });
    }
}