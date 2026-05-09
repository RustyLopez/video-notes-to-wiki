package com.chaostensor.video_notes_to_wiki.controller;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Value
@Builder
public class AnswerResponse {
    private String answer;
}