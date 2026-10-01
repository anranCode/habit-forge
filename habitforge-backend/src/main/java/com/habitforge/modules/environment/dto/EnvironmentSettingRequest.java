package com.habitforge.modules.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增环境设置
 */
@Data
public class EnvironmentSettingRequest {

    @Pattern(regexp = "^(PROMPT|RESISTANCE|COMMITMENT)$", message = "类型取值不合法")
    private String type;

    @Pattern(regexp = "^(PHONE|HABIT|OTHER)$", message = "类别取值不合法")
    private String category;

    @NotBlank(message = "请填写设置内容")
    @Size(max = 500, message = "设置内容最长 500 字")
    private String description;

    private String targetHabitId;

    /** 是否直接生效（默认 true） */
    private Boolean isActive;
}
