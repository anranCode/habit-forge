package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 加入错题本请求（upsert 语义）
 */
@Data
public class WrongQuestionAddRequest {

    @NotBlank(message = "题目ID不能为空")
    private String questionId;
}
