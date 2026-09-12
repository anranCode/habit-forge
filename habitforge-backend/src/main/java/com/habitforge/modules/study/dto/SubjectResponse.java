package com.habitforge.modules.study.dto;

import com.habitforge.modules.study.entity.Subject;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 科目响应（含实时章节汇总与考试倒计时; dueCards/wrongCount P0 恒 0, P1 补齐）
 */
@Data
@Builder
public class SubjectResponse {

    private String id;
    private String name;
    private LocalDate examDate;
    private String examSession;
    private String description;
    private Integer sortOrder;

    /** 章节总数(实时 COUNT) */
    private Integer chapterTotal;
    /** 已完成章节数(实时 COUNT) */
    private Integer chapterDone;
    /** 考试倒计时天数(今天到 examDate; 过期按 0; 无考期 null) */
    private Integer daysLeft;
    /** 到期闪卡数(P1 实现, P0 恒 0) */
    private Integer dueCards;
    /** 待复习错题数(P1 实现, P0 恒 0) */
    private Integer wrongCount;

    public static SubjectResponse from(Subject subject) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .name(subject.getName())
                .examDate(subject.getExamDate())
                .examSession(subject.getExamSession())
                .description(subject.getDescription())
                .sortOrder(subject.getSortOrder())
                .chapterTotal(0)
                .chapterDone(0)
                .daysLeft(daysLeftOf(subject.getExamDate()))
                .dueCards(0)
                .wrongCount(0)
                .build();
    }

    /** examDate 距今天数, 过去按 0, 无考期 null */
    public static Integer daysLeftOf(LocalDate examDate) {
        if (examDate == null) {
            return null;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), examDate);
        return (int) Math.max(0, days);
    }
}
