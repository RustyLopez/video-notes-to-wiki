package com.chaostensor.video_notes_to_wiki.controller;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.UUID;

@Jacksonized
@Value
@Builder
public class QueryResponse {
    private ImmutableList<UUID> transcriptRawIds;
    private ImmutableList<UUID> transcriptExecutiveSummaryIds;
    private ImmutableList<UUID> transcriptsHierarchicalRollupIds;
}