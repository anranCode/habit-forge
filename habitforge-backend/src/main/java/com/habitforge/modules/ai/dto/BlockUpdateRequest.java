package com.habitforge.modules.ai.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑计划块（PUT /plans/blocks/{id}; null 字段不修改, 引用列传空串=清除）
 */
@Data
public class BlockUpdateRequest {

    private String blockType;

    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    /** HH:mm */
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "开始时间格式须为 HH:mm")
    private String startTime;

    /** HH:mm */
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "结束时间格式须为 HH:mm")
    private String endTime;

    private String habitId;

    private String subjectId;

    private String chapterId;
}
