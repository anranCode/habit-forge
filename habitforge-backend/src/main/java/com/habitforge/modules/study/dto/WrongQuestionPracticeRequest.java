package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 错题重练登记（自报答对/答错, 不做自动判分）
 */
@Data
public class WrongQuestionPracticeRequest {

    @NotNull(message = "correct 不能为空")
    private Boolean correct;
}
