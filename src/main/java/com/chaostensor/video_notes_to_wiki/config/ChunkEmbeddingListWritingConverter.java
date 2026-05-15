package com.chaostensor.video_notes_to_wiki.config;

import com.chaostensor.video_notes_to_wiki.dto.ChunkEmbeddingList;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import tools.jackson.databind.json.JsonMapper;

@WritingConverter
public class ChunkEmbeddingListWritingConverter implements Converter<ChunkEmbeddingList, Json> {
    private final JsonMapper jsonMapper;

    public ChunkEmbeddingListWritingConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Json convert(ChunkEmbeddingList source) {
        try {
            return Json.of(jsonMapper.writeValueAsString(source));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}