package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 手动摘除/恢复错题
 */
@Data
public class WrongQuestionMasteredRequest {

    @NotNull(message = "mastered 不能为空")
    private Boolean mastered;
}
