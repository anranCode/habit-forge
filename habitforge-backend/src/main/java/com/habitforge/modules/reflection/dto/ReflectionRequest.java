package com.habitforge.modules.reflection.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建习惯心得请求（仅 result 必填）
 */
@Data
public class ReflectionRequest {

    @NotBlank(message = "日记ID不能为空")
    private String journalId;

    @NotBlank(message = "习惯ID不能为空")
    private String habitId;

    /** 完成情况: 1完成 0未完成 */
    @NotNull(message = "完成情况不能为空")
    @Min(value = 0, message = "完成情况取值 0/1")
    @Max(value = 1, message = "完成情况取值 0/1")
    private Integer result;

    /** 今日感受: 1好 2一般 3糟糕 */
    @Min(value = 1, message = "感受取值 1-3")
    @Max(value = 3, message = "感受取值 1-3")
    private Integer feeling;

    /** 难度: 1-5星 */
    @Min(value = 1, message = "难度取值 1-5")
    @Max(value = 5, message = "难度取值 1-5")
    private Integer difficulty;

    @Size(max = 500, message = "原因最长 500 字")
    private String reason;

    @Size(max = 500, message = "困难描述最长 500 字")
    private String obstacle;

    @Size(max = 500, message = "收获最长 500 字")
    private String learning;

    @Size(max = 500, message = "调整计划最长 500 字")
    private String adjustment;
}
