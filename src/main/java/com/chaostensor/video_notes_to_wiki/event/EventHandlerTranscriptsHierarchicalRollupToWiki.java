package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.TranscriptsHierarchicalRollup;
import com.chaostensor.video_notes_to_wiki.entity.Wiki;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMRequest;
import com.chaostensor.video_notes_to_wiki.llmclient.LLMResponse;
import com.chaostensor.video_notes_to_wiki.repository.WikiRepository;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class EventHandlerTranscriptsHierarchicalRollupToWiki implements EventHandler<TranscriptsHierarchicalRollup> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerTranscriptsHierarchicalRollupToWiki.class);

    private final EventStream<TranscriptsHierarchicalRollup> compressedTranscriptsEventStream;
    private final WebClient webClient;
    private final WikiRepository wikiRepository;
    private final EventStream<Wiki> wikiResultEventStream;
    private final VectorStore vectorStore;
    @Value("${app.wiki-output}")
    private String outputDirectory;

    private static final String SYNTHESIS_PROMPT_TEMPLATE = """
            You are an expert knowledge architect building a comprehensive internal wiki from a series of videos.
            
            Here is the polished documentation synthesized from each individual video:
            
            {{ALL_WIKI_READY_TRANSCRIPTS_FOR_A_GIVEN_JOB}}
            
            Additional relevant chunks from the vector database:
            
            {{RELEVANT_CHUNKS}}
            
            Synthesize all of this into a coherent, hierarchical knowledge base. Produce:
            
            1. **Master Executive Summary** — one strong paragraph covering the entire series
            2. **Hierarchical Topic Structure** — organize the content into logical parent topics and subtopics (use markdown headings)
            3. **Cross-Video Insights & Connections** — highlight how ideas from different videos relate, reinforce each other, or contradict
            4. **Consolidated Action Item Tracker** — all action items with video references
            5. **Recommended Wiki Structure** — suggest actual wiki pages with titles and outline of sections for each page
            6. **Knowledge Gaps or Follow-up Topics** (if any)
            
            Focus on creating something a new engineer could read and rapidly understand the key decisions, architecture, and current state of the project. Remove duplication across videos. Create clean hierarchy.
            """;

    public EventHandlerTranscriptsHierarchicalRollupToWiki(final EventStream<TranscriptsHierarchicalRollup> compressedTranscriptsEventStream,
                                                           final WebClient.Builder webClientBuilder,
                                                           final WikiRepository wikiRepository,
                                                           final EventStream<Wiki> wikiResultEventStream,
                                                           final VectorStore vectorStore) {
        this.compressedTranscriptsEventStream = compressedTranscriptsEventStream;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082/llm").build();
        this.wikiRepository = wikiRepository;
        this.wikiResultEventStream = wikiResultEventStream;
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void subscribe() {
        compressedTranscriptsEventStream.getEventStream()
                .flatMap(this::processCompressedTranscriptsEvent)
                .subscribe(
                        null, // onNext
                        error -> logger.error("Error in WikiReadyTranscript event stream subscription", error),
                        () -> logger.info("WikiReadyTranscript event stream completed")
                );
        logger.info("Subscribed to WikiReadyTranscript event stream");
    }

    private Mono<Void> processCompressedTranscriptsEvent(final TranscriptsHierarchicalRollup transcriptsHierarchicalRollup) {
        final String allTranscripts = transcriptsHierarchicalRollup.getCompressedResult();

        // Fetch most relevant chunks
        /*
         * This first step is key to adding essentially a second degree of information from the hierarchical rollup, based
         * on what was in the hierarchical rollup.
         *
         * So the rollup is a summary of summaries, but it would be lossy. HOWEVER. Using the summary as query
         * can be used to go do one last search of the entire knowledge base to see if anything strongly correlating to that
         * summary can be pulled in and added to the final context.
         */
        return Mono.fromCallable(() -> {
                    final SearchRequest searchRequest = SearchRequest.builder().query(transcriptsHierarchicalRollup.getCompressedResult()).topK(100).build();
                    final List<Document> docs = vectorStore.similaritySearch(searchRequest);
                    return docs.stream().map(Document::getText).toList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(relevantChunks -> {
                    final String combinedChunks = String.join("\n\n", relevantChunks);
                    final String prompt = SYNTHESIS_PROMPT_TEMPLATE
                            .replace("{{ALL_WIKI_READY_TRANSCRIPTS_FOR_A_GIVEN_JOB}}", allTranscripts)
                            .replace("{{RELEVANT_CHUNKS}}", combinedChunks);
                    return callLLM(prompt);
                })
                .flatMap(result -> {
                    final Wiki wiki = new Wiki();
                    wiki.setId(UUID.randomUUID());
                    wiki.setTranscriptId(transcriptsHierarchicalRollup.getId()); // Link to the compressed transcripts
                    wiki.setResult(result);
                    wiki.setCreatedAt(LocalDateTime.now());
                    wiki.setUpdatedAt(LocalDateTime.now());
                    return wikiRepository.save(wiki);
                })
                .flatMap(saved -> wikiResultEventStream.publish(saved).thenReturn(saved))
                .doOnNext(saved -> logger.info("Saved and published WikiResult id: {} triggered by compressed transcripts: {}", saved.getId(), transcriptsHierarchicalRollup.getId()))
                .flatMap(saved -> processWikiPostProcessing(saved).thenReturn(saved))
                .then()
                .onErrorResume(e -> {
                    logger.error("Error processing CompressedTranscripts event", e);
                    for (final StackTraceElement element : e.getStackTrace()) {
                        logger.error(element.toString());
                    }
                    return Mono.empty();
                });
    }


    private Mono<Void> processWikiPostProcessing(final Wiki wiki) {
        return Mono.fromCallable(() -> chunkWikiByPage(wiki.getResult()))
                .flatMap(chunks -> {
                    if (chunks.isEmpty()) {
                        return Mono.empty();
                    }
                    final List<Document> documents = chunks.stream()
                            .map(chunk -> new Document(chunk, Map.of("transcriptId", wiki.getId().toString(), "type", "hierarchical")))
                            .toList();
                    vectorStore.add(documents);
                    return Mono.empty();
                })
                .then(Mono.fromCallable(() -> zipAndSaveWiki(wiki)))
                .then();
    }

    private List<String> chunkWikiByPage(final String wikiResult) {
        final ImmutableList.Builder<String> chunks = ImmutableList.builder();
        // Simple regex to split by level 1 headings (# )
        final Pattern pattern = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
        final Matcher matcher = pattern.matcher(wikiResult);
        int lastEnd = 0;
        while (matcher.find()) {
            if (lastEnd > 0) {
                final String chunk = wikiResult.substring(lastEnd, matcher.start()).trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
            }
            lastEnd = matcher.start();
        }
        // Add the last chunk
        if (lastEnd < wikiResult.length()) {
            final String chunk = wikiResult.substring(lastEnd).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }
        return chunks.build();
    }

    private Void zipAndSaveWiki(final Wiki wiki) throws IOException {
        // Ensure output directory exists
        final Path outputDir = Paths.get(outputDirectory);
        Files.createDirectories(outputDir);

        // Generate filename with timestamp
        final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        final String filename = "wiki-" + timestamp + ".zip";
        final Path zipFilePath = outputDir.resolve(filename);

        try (final FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             final ZipOutputStream zos = new ZipOutputStream(fos)) {
            final ZipEntry entry = new ZipEntry("wiki.md");
            zos.putNextEntry(entry);
            zos.write(wiki.getResult().getBytes());
            zos.closeEntry();
        }

        logger.info("Zipped wiki result to {}", zipFilePath);
        return null;
    }

    private Mono<String> callLLM(final String prompt) {
        return webClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LLMRequest.builder().prompt(prompt).build())
                .retrieve()
                .bodyToMono(LLMResponse.class)
                .map(LLMResponse::getResult)
                .onErrorResume(e -> {
                    logger.error("Error calling LLM for synthesis", e);
                    for (final StackTraceElement element : e.getStackTrace()) {
                        logger.error(element.toString());
                    }
                    return Mono.error(e);
                });
    }
}