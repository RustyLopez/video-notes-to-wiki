package com.chaostensor.video_notes_to_wiki.config;

import com.chaostensor.video_notes_to_wiki.dto.ChunkEmbeddingList;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import tools.jackson.databind.json.JsonMapper;

@ReadingConverter
public class ChunkEmbeddingListReadingConverter implements Converter<Json, ChunkEmbeddingList> {
    private final JsonMapper jsonMapper;

    public ChunkEmbeddingListReadingConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ChunkEmbeddingList convert(Json source) {
        try {
            return jsonMapper.readValue(source.asString(), ChunkEmbeddingList.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}