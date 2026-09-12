package com.habitforge.modules.study.dto;

import com.habitforge.modules.study.entity.WrongQuestion;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题响应（附题目详情, 列表组装时两次 IN 查询防 N+1）
 */
@Data
@Builder
public class WrongQuestionResponse {

    private String questionId;
    private QuestionResponse question;
    private Integer wrongCount;
    private Integer correctStreak;
    private Boolean mastered;
    private LocalDateTime lastWrongAt;
    private LocalDateTime lastPracticedAt;

    public static WrongQuestionResponse from(WrongQuestion wrong, QuestionResponse question) {
        return WrongQuestionResponse.builder()
                .questionId(wrong.getQuestionId())
                .question(question)
                .wrongCount(wrong.getWrongCount())
                .correctStreak(wrong.getCorrectStreak())
                .mastered(wrong.getMastered() != null && wrong.getMastered() == 1)
                .lastWrongAt(wrong.getLastWrongAt())
                .lastPracticedAt(wrong.getLastPracticedAt())
                .build();
    }
}
