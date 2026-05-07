package com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@AllArgsConstructor
@Accessors(fluent = true)
@Builder
public record CompletedStatus(String transcriptData) implements WhisperStatus {

}