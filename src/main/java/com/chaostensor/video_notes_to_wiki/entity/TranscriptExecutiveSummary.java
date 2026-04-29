package com.chaostensor.video_notes_to_wiki.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("transcript_executive_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptExecutiveSummary {

    @Id
    private UUID id;
    private UUID transcriptLogicallyOrganizedId;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}