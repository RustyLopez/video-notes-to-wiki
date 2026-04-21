package com.chaostensor.video_notes_to_wiki.event;

import com.chaostensor.video_notes_to_wiki.entity.Job;
import com.chaostensor.video_notes_to_wiki.entity.SimplifiedTranscript;
import com.chaostensor.video_notes_to_wiki.entity.WikiReadyTranscript;
import com.chaostensor.video_notes_to_wiki.entity.WikiReadyTranscriptCompletionGroup;
import com.chaostensor.video_notes_to_wiki.repository.JobRepository;
import com.chaostensor.video_notes_to_wiki.repository.SimplifiedTranscriptRepository;
import com.chaostensor.video_notes_to_wiki.repository.WikiReadyTranscriptCompletionGroupRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class WikiReadyTranscriptCompletionSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(WikiReadyTranscriptCompletionSubscriber.class);

    private final WikiReadyTranscriptEventPublisher wikiReadyTranscriptEventPublisher;
    private final WikiReadyTranscriptCompletionGroupRepository completionGroupRepository;
    private final SimplifiedTranscriptRepository simplifiedTranscriptRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final WikiReadyTranscriptCompletionGroupEventPublisher completionGroupEventPublisher;
    private Disposable subscription;

    public WikiReadyTranscriptCompletionSubscriber(WikiReadyTranscriptEventPublisher wikiReadyTranscriptEventPublisher,
                                                   WikiReadyTranscriptCompletionGroupRepository completionGroupRepository,
                                                   SimplifiedTranscriptRepository simplifiedTranscriptRepository,
                                                   JobRepository jobRepository,
                                                   ObjectMapper objectMapper,
                                                   WikiReadyTranscriptCompletionGroupEventPublisher completionGroupEventPublisher) {
        this.wikiReadyTranscriptEventPublisher = wikiReadyTranscriptEventPublisher;
        this.completionGroupRepository = completionGroupRepository;
        this.simplifiedTranscriptRepository = simplifiedTranscriptRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.completionGroupEventPublisher = completionGroupEventPublisher;
    }

    @PostConstruct
    public void subscribe() {
        subscription = wikiReadyTranscriptEventPublisher.getEventStream()
            .flatMap(this::processWikiReadyTranscriptEvent)
            .subscribe(
                null, // onNext
                error -> logger.error("Error in WikiReadyTranscript event stream subscription", error),
                () -> logger.info("WikiReadyTranscript event stream completed")
            );
        logger.info("Subscribed to WikiReadyTranscript event stream");
    }

    private Mono<Void> processWikiReadyTranscriptEvent(WikiReadyTranscript wikiReadyTranscript) {
        return simplifiedTranscriptRepository.findById(wikiReadyTranscript.getSimplifiedTranscriptId())
            .flatMap(simplifiedTranscript -> {
                UUID jobId = simplifiedTranscript.getJobId();
                String transcriptSubId = simplifiedTranscript.getTranscriptSubId();
                return completionGroupRepository.findByJobId(jobId)
                    .switchIfEmpty(Mono.defer(() -> createNewCompletionGroup(jobId)))
                    .flatMap(group -> updateCompletionGroup(group, transcriptSubId, jobId));
            })
            .onErrorResume(e -> {
                logger.error("Error processing WikiReadyTranscript event", e);
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
                return Mono.empty();
            });
    }

    private Mono<WikiReadyTranscriptCompletionGroup> createNewCompletionGroup(UUID jobId) {
        WikiReadyTranscriptCompletionGroup group = new WikiReadyTranscriptCompletionGroup();
        group.setId(UUID.randomUUID());
        group.setJobId(jobId);
        group.setTranscriptSubIdsJson("[]");
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        return completionGroupRepository.save(group);
    }

    private Mono<Void> updateCompletionGroup(WikiReadyTranscriptCompletionGroup group, String transcriptSubId, UUID jobId) {
        try {
            List<String> subIds = objectMapper.readValue(group.getTranscriptSubIdsJson(), new TypeReference<List<String>>() {});
            if (subIds.contains(transcriptSubId)) {
                logger.warn("WikiReadyTranscript record for transcriptSubId {} already processed for jobId {}", transcriptSubId, jobId);
                return Mono.empty();
            }
            subIds.add(transcriptSubId);
            group.setTranscriptSubIdsJson(objectMapper.writeValueAsString(subIds));
            group.setUpdatedAt(LocalDateTime.now());
            return completionGroupRepository.save(group)
                .flatMap(saved -> checkAndEmitCompletion(saved, jobId))
                .then();
        } catch (Exception e) {
            logger.error("Error updating completion group", e);
            for (StackTraceElement element : e.getStackTrace()) {
                logger.error(element.toString());
            }
            return Mono.error(e);
        }
    }

    private Mono<Void> checkAndEmitCompletion(WikiReadyTranscriptCompletionGroup group, UUID jobId) {
        return jobRepository.findById(jobId)
            .flatMap(job -> {
                try {
                    List<String> jobSubIds = objectMapper.readValue(job.getTranscriptsJson(), new TypeReference<List<String>>() {});
                    List<String> processedSubIds = objectMapper.readValue(group.getTranscriptSubIdsJson(), new TypeReference<List<String>>() {});
                    if (processedSubIds.size() == jobSubIds.size()) {
                        return completionGroupEventPublisher.publish(group).then();
                    }
                    return Mono.empty();
                } catch (Exception e) {
                    logger.error("Error checking completion", e);
                    for (StackTraceElement element : e.getStackTrace()) {
                        logger.error(element.toString());
                    }
                    return Mono.error(e);
                }
            });
    }
}