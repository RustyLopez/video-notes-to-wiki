package com.chaostensor.video_notes_to_wiki.controller;

import java.util.List;
import java.util.UUID;

public record QueryResponse(
    List<UUID> transcriptRawIds,
    List<UUID> transcriptExecutiveSummaryIds,
    List<UUID> transcriptsHierarchicalRollupIds
) {
}