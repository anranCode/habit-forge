package com.habitforge.modules.study.dto;

import com.habitforge.modules.study.entity.Note;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记详情响应（含 Markdown 原文）
 */
@Data
@Builder
public class NoteResponse {

    private String id;
    private String subjectId;
    private String chapterId;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NoteResponse from(Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .subjectId(note.getSubjectId())
                .chapterId(note.getChapterId())
                .title(note.getTitle())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
