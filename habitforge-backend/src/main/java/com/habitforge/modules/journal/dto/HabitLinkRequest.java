package com.habitforge.modules.journal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 日记关联习惯请求
 */
@Data
public class HabitLinkRequest {

    @NotBlank(message = "习惯ID不能为空")
    private String habitId;
}
