package com.habitforge.modules.environment.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改环境设置（null = 不改）
 */
@Data
public class EnvironmentSettingUpdateRequest {

    @Pattern(regexp = "^(PROMPT|RESISTANCE|COMMITMENT)$", message = "类型取值不合法")
    private String type;

    @Pattern(regexp = "^(PHONE|HABIT|OTHER)$", message = "类别取值不合法")
    private String category;

    @Size(max = 500, message = "设置内容最长 500 字")
    private String description;

    /** 传空字符串表示解除关联; 不传 = 不改 */
    private String targetHabitId;

    private Boolean isActive;
}
