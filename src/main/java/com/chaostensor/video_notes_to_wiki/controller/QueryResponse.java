package com.chaostensor.video_notes_to_wiki.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {
    private List<UUID> transcriptRawIds;
    private List<UUID> transcriptExecutiveSummaryIds;
    private List<UUID> transcriptsHierarchicalRollupIds;
}