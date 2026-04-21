package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import com.chaostensor.video_notes_to_wiki.entity.WikiReadyTranscript;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.WikiReadyTranscriptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class WikiReadyTranscriptService {

    private static final Logger logger = LoggerFactory.getLogger(WikiReadyTranscriptService.class);

    private final WikiReadyTranscriptRepository wikiReadyTranscriptRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

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

    public WikiReadyTranscriptService(WikiReadyTranscriptRepository wikiReadyTranscriptRepository,
                                      WebClient.Builder webClientBuilder,
                                      ObjectMapper objectMapper) {
        this.wikiReadyTranscriptRepository = wikiReadyTranscriptRepository;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build();
        this.objectMapper = objectMapper;
    }

    public Mono<Void> processSimplifiedTranscriptEvent(SimplifiedTranscript simplifiedTranscript) {
        logger.info("Processing event for SimplifiedTranscript id: {}", simplifiedTranscript.getId());

        return wikiReadyTranscriptRepository.findBySimplifiedTranscriptId(simplifiedTranscript.getId())
            .flatMap(existing -> {
                logger.warn("WikiReadyTranscript already exists for SimplifiedTranscript id: {}, discarding event", simplifiedTranscript.getId());
                return Mono.empty();
            })
            .switchIfEmpty(Mono.defer(() -> createWikiReadyTranscript(simplifiedTranscript)))
            .then();
    }

    private Mono<WikiReadyTranscript> createWikiReadyTranscript(SimplifiedTranscript simplifiedTranscript) {
        String prompt = PROMPT_TEMPLATE.replace("{{SIMPLIFIED_TRANSCRIPT_DATA}}", simplifiedTranscript.getResult());

        return callLLM(prompt)
            .flatMap(result -> {
                WikiReadyTranscript wikiReadyTranscript = new WikiReadyTranscript();
                wikiReadyTranscript.setId(UUID.randomUUID());
                wikiReadyTranscript.setSimplifiedTranscriptId(simplifiedTranscript.getId());
                wikiReadyTranscript.setResult(result);
                wikiReadyTranscript.setCreatedAt(LocalDateTime.now());
                wikiReadyTranscript.setUpdatedAt(LocalDateTime.now());
                return wikiReadyTranscriptRepository.save(wikiReadyTranscript);
            })
            .doOnNext(saved -> logger.info("Saved WikiReadyTranscript id: {} for SimplifiedTranscript id: {}", saved.getId(), simplifiedTranscript.getId()))
            .doOnError(error -> {
                logger.error("Error processing WikiReadyTranscript for SimplifiedTranscript id: {}", simplifiedTranscript.getId(), error);
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
}