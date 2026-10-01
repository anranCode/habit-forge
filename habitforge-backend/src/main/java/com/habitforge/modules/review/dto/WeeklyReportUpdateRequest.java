package com.habitforge.modules.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 周报编辑请求（null = 不改该字段; AI 只出初稿, 终稿归用户）
 */
@Data
public class WeeklyReportUpdateRequest {

    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    @Min(value = 0, message = "评分取值 0-100")
    @Max(value = 100, message = "评分取值 0-100")
    private Integer score;

    @Size(max = 5000, message = "内容最长 5000 字")
    private String goodThings;

    @Size(max = 5000, message = "内容最长 5000 字")
    private String badThings;

    @Size(max = 5000, message = "内容最长 5000 字")
    private String learnings;

    @Size(max = 5000, message = "建议最长 5000 字")
    private String suggestions;
}
