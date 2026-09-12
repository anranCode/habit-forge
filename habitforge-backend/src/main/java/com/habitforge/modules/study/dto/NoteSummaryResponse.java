package com.habitforge.modules.study.dto;

import com.habitforge.modules.study.entity.Note;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记列表项（content 截断为摘要）
 */
@Data
@Builder
public class NoteSummaryResponse {

    private String id;
    private String subjectId;
    private String chapterId;
    private String title;
    /** 正文前 100 字(去 Markdown 符号) */
    private String excerpt;
    private LocalDateTime updatedAt;

    public static NoteSummaryResponse from(Note note, String excerpt) {
        return NoteSummaryResponse.builder()
                .id(note.getId())
                .subjectId(note.getSubjectId())
                .chapterId(note.getChapterId())
                .title(note.getTitle())
                .excerpt(excerpt)
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
