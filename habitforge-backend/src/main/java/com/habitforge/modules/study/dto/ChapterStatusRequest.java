package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 章节状态变更请求(PATCH /chapters/{id}/status)
 */
@Data
public class ChapterStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(NOT_STARTED|IN_PROGRESS|DONE)$", message = "状态取值不合法")
    private String status;
}
