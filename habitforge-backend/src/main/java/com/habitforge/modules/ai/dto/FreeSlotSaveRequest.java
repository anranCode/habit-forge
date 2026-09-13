package com.habitforge.modules.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 空闲时段整体覆盖保存（PUT /plans/free-slots; 语义校验 HH:mm/start<end/≤8/不重叠 → 6014）
 */
@Data
public class FreeSlotSaveRequest {

    /** yyyy-MM-dd, 不传 = 今天 */
    private LocalDate date;

    @NotNull(message = "空闲时段列表不能为空")
    @Valid
    private List<SlotItem> slots;

    @Data
    public static class SlotItem {

        @NotBlank(message = "开始时间不能为空")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "开始时间格式须为 HH:mm")
        private String startTime;

        @NotBlank(message = "结束时间不能为空")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "结束时间格式须为 HH:mm")
        private String endTime;

        @Size(max = 50, message = "标签最长 50 字")
        private String label;
    }
}
