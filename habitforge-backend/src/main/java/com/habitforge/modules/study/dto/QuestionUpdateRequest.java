package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 更新题目请求（各字段可选, null=不改）
 */
@Data
public class QuestionUpdateRequest {

    private String chapterId;

    private String questionType;

    private String stem;

    private List<QuestionOptionDTO> options;

    private String answer;

    private String analysis;

    private String sourceType;

    private String sourceDetail;

    @Min(value = 1, message = "难度最小 1")
    @Max(value = 5, message = "难度最大 5")
    private Integer difficulty;
}
