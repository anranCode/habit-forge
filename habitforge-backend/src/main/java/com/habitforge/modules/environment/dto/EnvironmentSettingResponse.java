package com.habitforge.modules.environment.dto;

import com.habitforge.modules.environment.entity.EnvironmentSetting;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EnvironmentSettingResponse {

    private String id;
    private String type;
    private String category;
    private String description;
    private String targetHabitId;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EnvironmentSettingResponse from(EnvironmentSetting s) {
        return EnvironmentSettingResponse.builder()
                .id(s.getId())
                .type(s.getType())
                .category(s.getCategory() == null ? EnvironmentSetting.CATEGORY_OTHER : s.getCategory())
                .description(s.getDescription())
                .targetHabitId(s.getTargetHabitId())
                .isActive(s.getIsActive() != null && s.getIsActive() == 1)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
