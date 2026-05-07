package com.chaostensor.video_notes_to_wiki.config;


import com.chaostensor.video_notes_to_wiki.entity.TranscriptExecutiveSummary;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptRaw;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptWithEmbeddings;
import com.chaostensor.video_notes_to_wiki.entity.TranscriptsHierarchicalRollup;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CustomConverters {


    @Bean
    public R2dbcCustomConversions customConversions(JsonMapper jsonMapper) {
        List<Converter<?, ?>> converters = new ArrayList<>();
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters);
    }

    /**
     * Critical. Gets me every time 0n a new project and really messed with grok.
     *
     * You can't pre-allocate an id.
     *
     * Thing is, you want to generate modern uuids that have a temporal component for index efficiency so we always write
     * to the end of the index, instead of somewhere in the middle of the tree, causing re-balancing.
     *
     * Some modern database scan tank it but the thing is it's not even just uuids. Sometimes you are replicating an id from
     * an external system and you want the id to be pre-allocated.
     *
     * anyway this wouldn't actually sole that latter case.. lol but uh yeh.. solves this one.
     *
     * R2dbc really needs a better pattern here.

     */
    @Bean
    BeforeConvertCallback<TranscriptExecutiveSummary> idGenerationTranscriptExecutiveSummary() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UuidCreator.getTimeOrderedEpoch());
            }
            return Mono.just(entity);
        };
    }
    @Bean
    BeforeConvertCallback<TranscriptsHierarchicalRollup> idGenerationTranscriptsHierarchicalRollup() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UuidCreator.getTimeOrderedEpoch());
            }
            return Mono.just(entity);
        };
    }
    @Bean
    BeforeConvertCallback<TranscriptWithEmbeddings> idGenerationTranscriptWithEmbeddings() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UuidCreator.getTimeOrderedEpoch());
            }
            return Mono.just(entity);
        };
    }

    @Bean
    BeforeConvertCallback<TranscriptRaw> idGenerationTranscriptRaw() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UuidCreator.getTimeOrderedEpoch());
            }
            return Mono.just(entity);
        };
    }

}