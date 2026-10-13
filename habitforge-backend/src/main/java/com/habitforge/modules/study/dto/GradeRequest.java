package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作答判分请求（刷题与错题重练共用同一管线）
 */
@Data
public class GradeRequest {

    @NotBlank(message = "题目ID不能为空")
    private String questionId;

    /** 与存储格式同构: 单选 "A"、多选 "A,C"、判断 "T"/"F"、主观自由文本(≤2000, 对齐 question_grades 列宽) */
    @NotBlank(message = "作答不能为空")
    @Size(max = 2000, message = "作答最长 2000 字")
    private String answer;
}
