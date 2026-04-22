package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.Job;
import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscriptStatus;
import com.chaostensor.video_notes_to_wiki.event.EventPublisher;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.JobRepository;
import com.chaostensor.video_notes_to_wiki.repository.SimplifiedTranscriptRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class SimplifiedTranscriptService {

    private final SimplifiedTranscriptRepository simplifiedTranscriptRepository;
    private final JobRepository jobRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EventPublisher<SimplifiedTranscript> eventPublisher;

    private static final String PROMPT_TEMPLATE = """
            You are an expert technical writer creating structured documentation from video transcripts.

            Here is the complete transcript from one video, provided as JSON with timestamps:

            {{TRANSCRIPT_JSON}}

            Break this transcript into logical topical sections based on subject changes (not arbitrary time cuts).

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

    public SimplifiedTranscriptService(SimplifiedTranscriptRepository simplifiedTranscriptRepository,
                                        JobRepository jobRepository,
                                        WebClient.Builder webClientBuilder,
                                        ObjectMapper objectMapper,
                                        EventPublisher<SimplifiedTranscript> eventPublisher) {
        this.simplifiedTranscriptRepository = simplifiedTranscriptRepository;
        this.jobRepository = jobRepository;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build(); // Assume LLM API at this URL
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public Mono<SimplifiedTranscript> processSimplifiedTranscript(UUID simplifiedTranscriptId) {
        return simplifiedTranscriptRepository.findById(simplifiedTranscriptId)
            .flatMap(simplifiedTranscript -> {
                simplifiedTranscript.setStatus(SimplifiedTranscriptStatus.PROCESSING);
                simplifiedTranscript.setUpdatedAt(LocalDateTime.now());
                return simplifiedTranscriptRepository.save(simplifiedTranscript)
                    .flatMap(saved -> processTranscript(saved));
            });
    }

    private Mono<SimplifiedTranscript> processTranscript(SimplifiedTranscript simplifiedTranscript) {
        return jobRepository.findById(simplifiedTranscript.getJobId())
            .flatMap(job -> {
                try {
                    Map<String, String> transcripts = objectMapper.readValue(job.getTranscriptsJson(), Map.class);
                    String transcript = transcripts.get(simplifiedTranscript.getTranscriptSubId());
                    if (transcript == null) {
                        simplifiedTranscript.setStatus(SimplifiedTranscriptStatus.FAILED);
                        simplifiedTranscript.setResult("Transcript not found");
                        return simplifiedTranscriptRepository.save(simplifiedTranscript);
                    }

                    // Format transcript as JSON
                    ObjectNode transcriptJson = objectMapper.createObjectNode().put("content", transcript);
                    String transcriptJsonStr = objectMapper.writeValueAsString(transcriptJson);

                    String prompt = PROMPT_TEMPLATE.replace("{{TRANSCRIPT_JSON}}", transcriptJsonStr);

                    return callLLM(prompt)
                        .flatMap(result -> {
                            simplifiedTranscript.setStatus(SimplifiedTranscriptStatus.COMPLETED);
                            simplifiedTranscript.setResult(result);
                            simplifiedTranscript.setUpdatedAt(LocalDateTime.now());
                            return simplifiedTranscriptRepository.save(simplifiedTranscript)
                                .flatMap(saved -> eventPublisher.publish(saved).thenReturn(saved));
                        })
                        .onErrorResume(e -> {
                            simplifiedTranscript.setStatus(SimplifiedTranscriptStatus.FAILED);
                            simplifiedTranscript.setResult("Error: " + e.getMessage());
                            simplifiedTranscript.setUpdatedAt(LocalDateTime.now());
                            return simplifiedTranscriptRepository.save(simplifiedTranscript);
                        });
                } catch (Exception e) {
                    simplifiedTranscript.setStatus(SimplifiedTranscriptStatus.FAILED);
                    simplifiedTranscript.setResult("Error parsing transcripts: " + e.getMessage());
                    return simplifiedTranscriptRepository.save(simplifiedTranscript);
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                simplifiedTranscript.setStatus(SimplifiedTranscriptStatus.FAILED);
                simplifiedTranscript.setResult("Job not found");
                return simplifiedTranscriptRepository.save(simplifiedTranscript);
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