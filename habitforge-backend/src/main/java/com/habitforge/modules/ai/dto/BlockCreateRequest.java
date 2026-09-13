package com.habitforge.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 手动新增计划块（POST /plans/blocks; MANUAL 块创建即 ADOPTED）
 */
@Data
public class BlockCreateRequest {

    /** yyyy-MM-dd */
    @NotNull(message = "计划日期不能为空")
    private LocalDate date;

    @NotBlank(message = "块类型不能为空")
    private String blockType;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    /** HH:mm */
    @NotBlank(message = "开始时间不能为空")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "开始时间格式须为 HH:mm")
    private String startTime;

    /** HH:mm */
    @NotBlank(message = "结束时间不能为空")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "结束时间格式须为 HH:mm")
    private String endTime;

    private String habitId;

    private String subjectId;

    private String chapterId;
}
