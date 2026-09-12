package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建题目请求（SINGLE/MULTI 需 options; JUDGE 答案 T/F; SHORT 参考答案）
 */
@Data
public class QuestionCreateRequest {

    @NotBlank(message = "科目ID不能为空")
    private String subjectId;

    /** 章节ID, null=不挂章节 */
    private String chapterId;

    /** SINGLE / MULTI / JUDGE / SHORT, 默认 SINGLE */
    private String questionType;

    @NotBlank(message = "题干不能为空")
    private String stem;

    /** 选项列表(SINGLE/MULTI 必填) */
    private List<QuestionOptionDTO> options;

    @NotBlank(message = "答案不能为空")
    private String answer;

    private String analysis;

    /** PAST_EXAM / TEXTBOOK / CUSTOM / AI, 空默认 CUSTOM(前端契约, 服务层不校验枚举) */
    private String sourceType;

    private String sourceDetail;

    @Min(value = 1, message = "难度最小 1")
    @Max(value = 5, message = "难度最大 5")
    private Integer difficulty;
}
