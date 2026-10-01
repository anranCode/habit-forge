package com.habitforge.modules.environment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 勾选/取消清单项
 */
@Data
public class EnvironmentSettingActiveRequest {

    @NotNull(message = "请提供 isActive")
    private Boolean isActive;
}
