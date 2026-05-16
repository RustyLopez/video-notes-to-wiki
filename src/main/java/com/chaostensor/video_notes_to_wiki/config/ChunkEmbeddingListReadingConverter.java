package com.chaostensor.video_notes_to_wiki.config;

import com.chaostensor.video_notes_to_wiki.dto.ChunkEmbeddingList;
import io.r2dbc.postgresql.codec.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import tools.jackson.databind.json.JsonMapper;

@ReadingConverter
public class ChunkEmbeddingListReadingConverter implements Converter<Json, ChunkEmbeddingList> {
    private static final Logger log = LoggerFactory.getLogger(ChunkEmbeddingListReadingConverter.class);
    private final JsonMapper jsonMapper;

    public ChunkEmbeddingListReadingConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ChunkEmbeddingList convert(Json source) {
        try {
            return jsonMapper.readValue(source.asString(), ChunkEmbeddingList.class);
        } catch (Exception e) {
            log.error("Failed to deserialize JSON to ChunkEmbeddingList", e);
            throw new RuntimeException(e);
        }
    }
}