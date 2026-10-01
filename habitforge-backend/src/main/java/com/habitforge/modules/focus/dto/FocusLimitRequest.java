package com.habitforge.modules.focus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设置每日娱乐时长上限（分钟）
 */
@Data
public class FocusLimitRequest {

    @NotNull(message = "请填写上限分钟数")
    @Min(value = 0, message = "上限不能为负")
    @Max(value = 24 * 60, message = "上限不能超过 1440 分钟")
    private Integer limitMinutes;
}
