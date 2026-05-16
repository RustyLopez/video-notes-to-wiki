package com.chaostensor.video_notes_to_wiki.config;

import com.chaostensor.video_notes_to_wiki.dto.ChunkEmbeddingList;
import io.r2dbc.postgresql.codec.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import tools.jackson.databind.json.JsonMapper;

@WritingConverter
public class ChunkEmbeddingListWritingConverter implements Converter<ChunkEmbeddingList, Json> {
    private static final Logger log = LoggerFactory.getLogger(ChunkEmbeddingListWritingConverter.class);
    private final JsonMapper jsonMapper;

    public ChunkEmbeddingListWritingConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Json convert(ChunkEmbeddingList source) {
        try {
            return Json.of(jsonMapper.writeValueAsString(source));
        } catch (Exception e) {
            log.error("Failed to serialize ChunkEmbeddingList to JSON", e);
            throw new RuntimeException(e);
        }
    }
}