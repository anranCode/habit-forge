package com.habitforge.modules.study.dto;

import com.habitforge.modules.study.entity.Flashcard;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 闪卡响应
 */
@Data
@Builder
public class FlashcardResponse {

    private String id;
    private String subjectId;
    private String chapterId;
    private String front;
    private String back;
    private BigDecimal easeFactor;
    private Integer intervalDays;
    private Integer repetition;
    private Integer lapses;
    private LocalDate dueDate;
    private LocalDateTime lastReviewedAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FlashcardResponse from(Flashcard card) {
        return FlashcardResponse.builder()
                .id(card.getId())
                .subjectId(card.getSubjectId())
                .chapterId(card.getChapterId())
                .front(card.getFront())
                .back(card.getBack())
                .easeFactor(card.getEaseFactor())
                .intervalDays(card.getIntervalDays())
                .repetition(card.getRepetition())
                .lapses(card.getLapses())
                .dueDate(card.getDueDate())
                .lastReviewedAt(card.getLastReviewedAt())
                .status(card.getStatus())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }
}
