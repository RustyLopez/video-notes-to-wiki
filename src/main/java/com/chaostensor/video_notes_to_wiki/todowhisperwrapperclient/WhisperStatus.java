package com.chaostensor.video_notes_to_wiki.todowhisperwrapperclient;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status", visible = false)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PendingStatus.class, name = "pending"),
    @JsonSubTypes.Type(value = FailedStatus.class, name = "failed"),
    @JsonSubTypes.Type(value = CompletedStatus.class, name = "completed")
})
public interface WhisperStatus {
}