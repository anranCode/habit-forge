package com.habitforge.modules.study.dto;

import com.habitforge.modules.study.entity.Question;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目响应（options 已由服务层从 JSON 字符串反序列化为列表）
 */
@Data
@Builder
public class QuestionResponse {

    private String id;
    private String subjectId;
    private String chapterId;
    private String questionType;
    private String stem;
    /** SINGLE/MULTI 为选项列表; JUDGE/SHORT 为 null */
    private List<QuestionOptionDTO> options;
    private String answer;
    private String analysis;
    private String sourceType;
    private String sourceDetail;
    private Integer difficulty;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static QuestionResponse from(Question question, List<QuestionOptionDTO> options) {
        return QuestionResponse.builder()
                .id(question.getId())
                .subjectId(question.getSubjectId())
                .chapterId(question.getChapterId())
                .questionType(question.getQuestionType())
                .stem(question.getStem())
                .options(options)
                .answer(question.getAnswer())
                .analysis(question.getAnalysis())
                .sourceType(question.getSourceType())
                .sourceDetail(question.getSourceDetail())
                .difficulty(question.getDifficulty())
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .build();
    }
}
