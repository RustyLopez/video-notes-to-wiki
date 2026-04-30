package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.config.ChunkingConfig;
import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.service.EmbeddingService;
import io.jchunk.fixed.FixedChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

@Component
public class EventHandlerTranscriptRawToTranscriptWithEmbeddings implements EventHandler<TranscriptRaw> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerTranscriptRawToTranscriptWithEmbeddings.class);

    private final EventStream<TranscriptRaw> transcriptEventStream;
    private final TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository;
    private final TranscriptRepository transcriptRepository;
    private final ChunkingConfig chunkingConfig;
    private final LlmConfig llmConfig;
    private final EmbeddingService embeddingService;
    private final EventStream<TranscriptWithEmbeddings> eventStream;
    private Disposable subscription;

    public EventHandlerTranscriptRawToTranscriptWithEmbeddings(EventStream<TranscriptRaw> transcriptEventStream,
                                                                      TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository,
                                                                      TranscriptRepository transcriptRepository,
                                                                      ChunkingConfig chunkingConfig,
                                                                      LlmConfig llmConfig,
                                                                      EmbeddingService embeddingService,
                                                                      EventStream<TranscriptWithEmbeddings> eventStream) {
        this.transcriptEventStream = transcriptEventStream;
        this.transcriptWithEmbeddingsRepository = transcriptWithEmbeddingsRepository;
        this.transcriptRepository = transcriptRepository;
        this.chunkingConfig = chunkingConfig;
        this.llmConfig = llmConfig;
        this.embeddingService = embeddingService;
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
        // Create a transcript with embeddings for this completed transcript
        TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());
        transcriptWithEmbeddings.setTranscriptRawId(transcriptRaw.getId());
        transcriptWithEmbeddings.setStatus(LlmStatus.PENDING);
        transcriptWithEmbeddings.setCreatedAt(LocalDateTime.now());
        transcriptWithEmbeddings.setUpdatedAt(LocalDateTime.now());

        return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings)
            .doOnNext(saved -> {
                // Start async processing to create chunks and embeddings
                processTranscriptWithEmbeddings(saved.getId()).subscribe();
            })
            .then();
    }

    private Mono<TranscriptWithEmbeddings> processTranscriptWithEmbeddings(UUID transcriptWithEmbeddingsId) {
        return transcriptWithEmbeddingsRepository.findById(transcriptWithEmbeddingsId)
            .flatMap(transcriptWithEmbeddings -> {
                transcriptWithEmbeddings.setStatus(LlmStatus.PROCESSING);
                transcriptWithEmbeddings.setUpdatedAt(LocalDateTime.now());
                return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings)
                    .flatMap(saved -> processTranscript(saved));
            });
    }

    private Mono<TranscriptWithEmbeddings> processTranscript(TranscriptWithEmbeddings transcriptWithEmbeddings) {
        return transcriptRepository.findById(transcriptWithEmbeddings.getTranscriptRawId())
            .flatMap(transcriptRaw -> {
                try {
                    String transcriptContent = transcriptRaw.getTranscript();
                    if (transcriptContent == null) {
                        transcriptWithEmbeddings.setStatus(LlmStatus.FAILED);
                        return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings);
                    }

                    // Perform simple chunking for now
                    List<String> chunks = List.of(transcriptContent); // Single chunk for now

                    // Generate embeddings for chunks
                    List<List<Float>> embeddings = embeddingService.embedTexts(chunks);
                    List<TranscriptWithEmbeddings.ChunkEmbedding> chunkEmbeddings = IntStream.range(0, chunks.size())
                        .mapToObj(i -> new TranscriptWithEmbeddings.ChunkEmbedding(chunks.get(i), embeddings.get(i)))
                        .toList();

                    transcriptWithEmbeddings.setChunkEmbeddings(chunkEmbeddings);
                    transcriptWithEmbeddings.setStatus(LlmStatus.COMPLETED);
                    transcriptWithEmbeddings.setUpdatedAt(LocalDateTime.now());

                    return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings)
                        .flatMap(saved -> eventStream.publish(saved).thenReturn(saved));

                } catch (Exception e) {
                    transcriptWithEmbeddings.setStatus(LlmStatus.FAILED);
                    transcriptWithEmbeddings.setUpdatedAt(LocalDateTime.now());
                    return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings);
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                transcriptWithEmbeddings.setStatus(LlmStatus.FAILED);
                return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings);
            }));
    }
}