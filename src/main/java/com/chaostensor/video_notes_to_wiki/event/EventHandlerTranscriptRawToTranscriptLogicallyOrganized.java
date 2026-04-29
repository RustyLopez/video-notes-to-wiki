package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptLogicallyOrganized;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptLogicallyOrganizedRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.UUID;
import java.time.LocalDateTime;

@Component
public class EventHandlerTranscriptRawToTranscriptLogicallyOrganized implements EventHandler<TranscriptRaw> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerTranscriptRawToTranscriptLogicallyOrganized.class);

    private final EventStream<TranscriptRaw> transcriptEventStream;
    private final TranscriptLogicallyOrganizedRepository transcriptLogicallyOrganizedRepository;
    private final TranscriptRepository transcriptRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EventStream<TranscriptLogicallyOrganized> eventStream;
    private Disposable subscription;

    private static final String PROMPT_TEMPLATE = """
            You are an expert technical writer creating structured documentation from video transcripts.

            Here is the complete transcriptRaw from one video, provided as JSON with timestamps:

            {{TRANSCRIPT_JSON}}

            Break this transcriptRaw into logical topical sections based on subject changes (not arbitrary time cuts).

            Produce a structured analysis that includes:

            - One high-quality executive summary paragraph for the entire video
            - A clear list of logical sections, each with:
              - Descriptive section title
              - Start and end timestamp
              - Concise but information-dense summary
              - Key points (as bullets)
              - Any action items, decisions, or open questions mentioned
              - Important technical concepts or terms introduced

            Also extract 6-12 relevant topic tags for the whole video.

            Be extremely accurate with timestamps and technical details. Do not hallucinate content.

            Respond with well-structured markdown using clear headings.
            """;

    public EventHandlerTranscriptRawToTranscriptLogicallyOrganized(EventStream<TranscriptRaw> transcriptEventStream,
                                                                    TranscriptLogicallyOrganizedRepository transcriptLogicallyOrganizedRepository,
                                                                    TranscriptRepository transcriptRepository,
                                                                    WebClient.Builder webClientBuilder,
                                                                    ObjectMapper objectMapper,
                                                                    EventStream<TranscriptLogicallyOrganized> eventStream) {
        this.transcriptEventStream = transcriptEventStream;
        this.transcriptLogicallyOrganizedRepository = transcriptLogicallyOrganizedRepository;
        this.transcriptRepository = transcriptRepository;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build();
        this.objectMapper = objectMapper;
        this.eventStream = eventStream;
    }

    @PostConstruct
    public void subscribe() {
        subscription = transcriptEventStream.getEventStream()
            .flatMap(this::processTranscriptEvent)
            .subscribe(
                null, // onNext
                error -> logger.error("Error in transcript event stream subscription", error),
                () -> logger.info("Transcript event stream completed")
            );
        logger.info("Subscribed to transcript event stream");
    }

    private reactor.core.publisher.Mono<Void> processTranscriptEvent(TranscriptRaw transcriptRaw) {
        // Create a simplified transcript for this completed transcript
        TranscriptLogicallyOrganized transcriptLogicallyOrganized = new TranscriptLogicallyOrganized();
        transcriptLogicallyOrganized.setId(UUID.randomUUID());
        transcriptLogicallyOrganized.setTransccriptRawId(transcriptRaw.getId());
        transcriptLogicallyOrganized.setStatus(LlmStatus.PENDING);
        transcriptLogicallyOrganized.setCreatedAt(LocalDateTime.now());
        transcriptLogicallyOrganized.setUpdatedAt(LocalDateTime.now());

        return transcriptLogicallyOrganizedRepository.save(transcriptLogicallyOrganized)
            .doOnNext(saved -> {
                // Start async processing to create simplified version
                processSimplifiedTranscript(saved.getId()).subscribe();
            })
            .then();
    }

    private Mono<TranscriptLogicallyOrganized> processSimplifiedTranscript(UUID simplifiedTranscriptId) {
        return transcriptLogicallyOrganizedRepository.findById(simplifiedTranscriptId)
            .flatMap(simplifiedTranscript -> {
                simplifiedTranscript.setStatus(LlmStatus.PROCESSING);
                simplifiedTranscript.setUpdatedAt(LocalDateTime.now());
                return transcriptLogicallyOrganizedRepository.save(simplifiedTranscript)
                    .flatMap(saved -> processTranscript(saved));
            });
    }

    private Mono<TranscriptLogicallyOrganized> processTranscript(TranscriptLogicallyOrganized transcriptLogicallyOrganized) {
        return transcriptRepository.findById(transcriptLogicallyOrganized.getTransccriptRawId())
            .flatMap(transcriptRaw -> {
                try {
                    String transcriptContent = transcriptRaw.getTranscript();
                    if (transcriptContent == null) {
                        transcriptLogicallyOrganized.setStatus(LlmStatus.FAILED);
                        transcriptLogicallyOrganized.setResult("Transcript not found");
                        return transcriptLogicallyOrganizedRepository.save(transcriptLogicallyOrganized);
                    }

                    // Format transcriptRaw as JSON
                    ObjectNode transcriptJson = objectMapper.createObjectNode().put("content", transcriptContent);
                    String transcriptJsonStr = objectMapper.writeValueAsString(transcriptJson);

                    String prompt = PROMPT_TEMPLATE.replace("{{TRANSCRIPT_JSON}}", transcriptJsonStr);

                    return callLLM(prompt)
                        .flatMap(result -> {
                            transcriptLogicallyOrganized.setStatus(LlmStatus.COMPLETED);
                            transcriptLogicallyOrganized.setResult(result);
                            transcriptLogicallyOrganized.setUpdatedAt(LocalDateTime.now());
                            return transcriptLogicallyOrganizedRepository.save(transcriptLogicallyOrganized)
                                .flatMap(saved -> eventStream.publish(saved).thenReturn(saved));
                        })
                        .onErrorResume(e -> {
                            transcriptLogicallyOrganized.setStatus(LlmStatus.FAILED);
                            transcriptLogicallyOrganized.setResult("Error: " + e.getMessage());
                            transcriptLogicallyOrganized.setUpdatedAt(LocalDateTime.now());
                            return transcriptLogicallyOrganizedRepository.save(transcriptLogicallyOrganized);
                        });
                } catch (Exception e) {
                    transcriptLogicallyOrganized.setStatus(LlmStatus.FAILED);
                    transcriptLogicallyOrganized.setResult("Error parsing transcripts: " + e.getMessage());
                    return transcriptLogicallyOrganizedRepository.save(transcriptLogicallyOrganized);
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                transcriptLogicallyOrganized.setStatus(LlmStatus.FAILED);
                transcriptLogicallyOrganized.setResult("Job not found");
                return transcriptLogicallyOrganizedRepository.save(transcriptLogicallyOrganized);
            }));
    }

    private Mono<String> callLLM(String prompt) {
        return webClient.post()
            .uri("")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LLMRequest.builder().prompt(prompt).build())
            .retrieve()
            .bodyToMono(LLMResponse.class)
            .map(LLMResponse::getResult)
            .onErrorResume(e -> Mono.just("Error calling LLM: " + e.getMessage()));
    }
}