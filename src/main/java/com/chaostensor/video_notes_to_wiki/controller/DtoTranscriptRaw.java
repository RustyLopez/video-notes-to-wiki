package com.chaostensor.video_notes_to_wiki.controller;


import com.chaostensor.video_notes_to_wiki.entity.LlmStatus;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

import java.util.UUID;

@Value
@Jacksonized
@Builder(toBuilder = true)
@With
public class DtoTranscriptRaw {

    UUID id;

    String videoPath;

    LlmStatus status;
}