package com.chaostensor.video_notes_to_wiki.event;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaHierarchicalRollupTest {

    @Test
    void chunkByBulletPointsSectionHeadersAndDoubleNewlines_shouldSplitCorrectly() {
        final String input = "# Heading1\n\nContent here.\n* bullet\n\nMore content";
        final ImmutableList<String> chunks = EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaHierarchicalRollup.chunkByBulletPointsSectionHeadersAndDoubleNewlines(input);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 1);
    }

    @Test
    void chunkByBulletPointsSectionHeadersAndDoubleNewlines_emptyInput() {
        final ImmutableList<String> chunks = EventHandlerCombineLatestAllTranscriptExecutiveSummariesToTranscriptsMasterExecutiveSummaryViaHierarchicalRollup.chunkByBulletPointsSectionHeadersAndDoubleNewlines("");
        assertTrue(chunks.isEmpty());
    }
}
