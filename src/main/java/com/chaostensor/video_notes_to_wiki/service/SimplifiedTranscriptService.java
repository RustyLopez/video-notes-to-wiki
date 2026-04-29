package com.chaostensor.video_notes_to_wiki.service;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptLogicallyOrganized;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.event.EventStream;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptLogicallyOrganizedRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import java.time.LocalDateTime;

@Service
public class SimplifiedTranscriptService {

    private final TranscriptLogicallyOrganizedRepository transcriptLogicallyOrganizedRepository;
    private final TranscriptRepository transcriptRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EventStream<TranscriptLogicallyOrganized> eventStream;

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

    public SimplifiedTranscriptService(TranscriptLogicallyOrganizedRepository transcriptLogicallyOrganizedRepository,
                                       TranscriptRepository transcriptRepository,
                                       WebClient.Builder webClientBuilder,
                                       ObjectMapper objectMapper,
                                       EventStream<TranscriptLogicallyOrganized> eventStream) {
        this.transcriptLogicallyOrganizedRepository = transcriptLogicallyOrganizedRepository;
        this.transcriptRepository = transcriptRepository;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build(); // Assume LLM API at this URL
        this.objectMapper = objectMapper;
        this.eventStream = eventStream;
    }

    public Mono<TranscriptLogicallyOrganized> processSimplifiedTranscript(UUID simplifiedTranscriptId) {
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