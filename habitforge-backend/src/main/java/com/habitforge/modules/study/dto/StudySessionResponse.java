package com.habitforge.modules.study.dto;

import com.habitforge.modules.study.entity.StudySession;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习记录响应（附科目/章节名, 便于前端直接渲染）
 */
@Data
@Builder
public class StudySessionResponse {

    private String id;
    private String subjectId;
    private String subjectName;
    private String chapterId;
    private String chapterName;
    private LocalDate sessionDate;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer minutes;
    /** TIMER / MANUAL */
    private String source;
    private String note;
    /** 是否仍在计时中（true 时 minutes/endedAt 为 null） */
    private Boolean running;

    public static StudySessionResponse of(StudySession s, String subjectName, String chapterName) {
        return StudySessionResponse.builder()
                .id(s.getId())
                .subjectId(s.getSubjectId())
                .subjectName(subjectName)
                .chapterId(s.getChapterId())
                .chapterName(chapterName)
                .sessionDate(s.getSessionDate())
                .startedAt(s.getStartedAt())
                .endedAt(s.getEndedAt())
                .minutes(s.getMinutes())
                .source(s.getSource())
                .note(s.getNote())
                .running(s.isRunning())
                .build();
    }
}
