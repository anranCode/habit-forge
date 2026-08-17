package com.habitforge.modules.reflection.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新习惯心得请求（字段均可选，null 表示不修改）
 */
@Data
public class ReflectionUpdateRequest {

    @Min(value = 0, message = "完成情况取值 0/1")
    @Max(value = 1, message = "完成情况取值 0/1")
    private Integer result;

    @Min(value = 1, message = "感受取值 1-3")
    @Max(value = 3, message = "感受取值 1-3")
    private Integer feeling;

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
