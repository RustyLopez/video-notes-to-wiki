package com.chaostensor.video_notes_to_wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("transcript_raw")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptRaw {

    @Id
    private UUID id;
    private LlmStatus status;
    private String videoPath;
    private String transcript;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}