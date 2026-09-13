package com.habitforge.modules.ai.dto;

import com.habitforge.modules.ai.entity.PlanFreeSlot;
import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 空闲时段响应（时间序列化为 HH:mm, 与前端契约逐字一致）
 */
@Data
@Builder
public class PlanFreeSlotResponse {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private String id;
    /** HH:mm */
    private String startTime;
    /** HH:mm */
    private String endTime;
    private String label;
    private Integer sortOrder;

    public static PlanFreeSlotResponse from(PlanFreeSlot slot) {
        return PlanFreeSlotResponse.builder()
                .id(slot.getId())
                .startTime(fmt(slot.getStartTime()))
                .endTime(fmt(slot.getEndTime()))
                .label(slot.getLabel() == null ? "" : slot.getLabel())
                .sortOrder(slot.getSortOrder())
                .build();
    }

    public static String fmt(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }
}
