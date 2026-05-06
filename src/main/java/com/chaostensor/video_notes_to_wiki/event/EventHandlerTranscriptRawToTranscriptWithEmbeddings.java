package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.config.ChunkingConfig;
import com.chaostensor.video_notes_to_wiki.config.LlmConfig;
import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptRepository;
import com.chaostensor.video_notes_to_wiki.repository.TranscriptWithEmbeddingsRepository;
import com.chaostensor.video_notes_to_wiki.service.EmbeddingService;
import io.jchunk.core.chunk.Chunk;
import io.jchunk.fixed.Config;
import io.jchunk.fixed.FixedChunker;
import io.jchunk.recursive.RecursiveCharacterChunker;
import io.jchunk.semantic.SemanticChunker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Component
public class EventHandlerTranscriptRawToTranscriptWithEmbeddings implements EventHandler<TranscriptRaw> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerTranscriptRawToTranscriptWithEmbeddings.class);

    private final EventStream<TranscriptRaw> transcriptEventStream;
    private final TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository;
    private final TranscriptRepository transcriptRepository;
    private final LlmConfig llmConfig;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final EventStream<TranscriptWithEmbeddings> eventStream;

    public EventHandlerTranscriptRawToTranscriptWithEmbeddings(final EventStream<TranscriptRaw> transcriptEventStream,
                                                               final TranscriptWithEmbeddingsRepository transcriptWithEmbeddingsRepository,
                                                               final TranscriptRepository transcriptRepository,
                                                               final LlmConfig llmConfig,
                                                               final EmbeddingService embeddingService,
                                                               final VectorStore vectorStore,
                                                               final EventStream<TranscriptWithEmbeddings> eventStream) {
        this.transcriptEventStream = transcriptEventStream;
        this.transcriptWithEmbeddingsRepository = transcriptWithEmbeddingsRepository;
        this.transcriptRepository = transcriptRepository;
        this.llmConfig = llmConfig;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.eventStream = eventStream;
    }

    @PostConstruct
    public void subscribe() {
        transcriptEventStream.getEventStream()
                .flatMap(this::processTranscriptEvent)
                .subscribe(
                        null,
                        error -> logger.error("Error in transcript event stream subscription", error),
                        () -> logger.info("Transcript event stream completed")
                );
        logger.info("Subscribed to transcript event stream");
    }

    reactor.core.publisher.Mono<Void> processTranscriptEvent(final TranscriptRaw transcriptRaw) {
        // Create a transcript with embeddings for this completed transcript
        final TranscriptWithEmbeddings transcriptWithEmbeddings = new TranscriptWithEmbeddings();
        transcriptWithEmbeddings.setId(UUID.randomUUID());
        transcriptWithEmbeddings.setTranscriptRawId(transcriptRaw.getId());
        transcriptWithEmbeddings.setStatus(LlmStatus.PENDING);
        transcriptWithEmbeddings.setCreatedAt(LocalDateTime.now());
        transcriptWithEmbeddings.setUpdatedAt(LocalDateTime.now());

        return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings)
                .doOnNext(saved -> {
                    // Start async processing to create chunks and embeddings
                    processTranscriptWithEmbeddings(saved.getId()).subscribe(v -> {
                    }, error -> logger.error("Error processing transcript with embeddings", error));
                })
                .then();
    }

    Mono<TranscriptWithEmbeddings> processTranscriptWithEmbeddings(final UUID transcriptWithEmbeddingsId) {
        return transcriptWithEmbeddingsRepository.findById(transcriptWithEmbeddingsId)
                .flatMap(transcriptWithEmbeddings -> {
                    transcriptWithEmbeddings.setStatus(LlmStatus.PROCESSING);
                    transcriptWithEmbeddings.setUpdatedAt(LocalDateTime.now());
                    return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings)
                            .flatMap(saved -> processTranscript(saved));
                });
    }

    Mono<TranscriptWithEmbeddings> processTranscript(final TranscriptWithEmbeddings transcriptWithEmbeddings) {
        return transcriptRepository.findById(transcriptWithEmbeddings.getTranscriptRawId())
                .flatMap(transcriptRaw -> {
                    try {
                        final String transcriptContent = transcriptRaw.getTranscript();
                        if (transcriptContent == null) {
                            transcriptWithEmbeddings.setStatus(LlmStatus.FAILED);
                            return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings);
                        }

                        /*
                         *  The SemanticChunker uses sentence level embeddings to detect semantic differences and so must be redundantly
                         *  here in the semantic chunk in addition to subsequently to produce embeddings for the entire chunk.
                         *
                         *  This allows for semantic base chunking without a hit to an LLM ( though still a remote hit for
                         *  our current embedder solution ).
                         *
                         *  But it does mean we use the embedder in two locations here.
                         *
                         *  Firs to detect difference ranking between sentences to detect boundaries.
                         *
                         *  But then to actually generate the embeddings for the entire chunk.
                         */
                        final int maxIdealChunkSize = determineIdealMaxChunkSizeForSingleTranscriptChunks();
                        final SemanticChunker chunker = new SemanticChunker(embeddingService::embed);
                        final RecursiveCharacterChunker sentenceChunker = new RecursiveCharacterChunker(io.jchunk.recursive.Config.builder().chunkSize(maxIdealChunkSize).build());
                        // TODO configure this fixed chunker better
                        final FixedChunker fixedChunker = new FixedChunker(Config.builder().chunkSize(maxIdealChunkSize).build());
                        final List<String> chunks = chunker.split(transcriptContent).stream()
                                .map(Chunk::content)
                                .flatMap(c -> {
                                    /*
                                     * If semantic chunking produced too long of a chunk, then we have to chunk this chunk further
                                     * based on a more arbitrary mechanism like sentences and/or fixed length.
                                     *
                                     * we'll do both with sentences taking priority and faling back in another flatmap
                                     * to fixed length if all else fails.
                                     */
                                    if (c.length() < maxIdealChunkSize) {
                                        return Stream.of(c);
                                    }
                                    return sentenceChunker.split(c).stream().map(Chunk::content);
                                }).flatMap(c -> {
                                    if (c.length() < maxIdealChunkSize) {
                                        return Stream.of(c);
                                    }
                                    return fixedChunker.split(c).stream().map(Chunk::content);
                                }).collect(Collectors.toList());

                        // Generate embeddings for chunks (keep for db storage)
                        final List<float[]> embeddings = embeddingService.embed(chunks);
                        final List<TranscriptWithEmbeddings.ChunkEmbedding> chunkEmbeddings = IntStream.range(0, chunks.size())
                                .mapToObj(i -> new TranscriptWithEmbeddings.ChunkEmbedding(chunks.get(i), embeddings.get(i)))
                                .toList();

                        transcriptWithEmbeddings.setChunkEmbeddings(chunkEmbeddings);
                        transcriptWithEmbeddings.setStatus(LlmStatus.COMPLETED);
                        transcriptWithEmbeddings.setUpdatedAt(LocalDateTime.now());

                        return transcriptWithEmbeddingsRepository.save(transcriptWithEmbeddings)
                                .flatMap(saved -> {
                                    // Save chunks to vector database
                                    final List<Document> documents = chunks.stream()
                                            .map(chunk -> new Document(chunk, Map.of("transcriptId", transcriptRaw.getId().toString(), "type", "chunk")))
                                            .toList();
                                    vectorStore.add(documents);
                                    return Mono.just(saved);
                                })
                                .flatMap(saved -> eventStream.publish(saved).thenReturn(saved));

                    } catch (final Exception e) {
                        logger.error("Error processing transcript with embeddings for id: {}", transcriptWithEmbeddings.getId(), e);
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

    int determineIdealMaxChunkSizeForSingleTranscriptChunks() {
        return llmConfig.getMaxChunkTokens();
    }
}