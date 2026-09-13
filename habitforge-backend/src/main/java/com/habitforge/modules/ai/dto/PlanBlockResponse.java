package com.habitforge.modules.ai.dto;

import com.habitforge.modules.ai.entity.PlanBlock;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计划块响应（habitName/subjectName/habitCheckedToday 为服务层富化只读字段）
 */
@Data
@Builder
public class PlanBlockResponse {

    private String id;
    private String planId;
    /** HABIT / STUDY / REST / OTHER */
    private String blockType;
    private String title;
    /** HH:mm */
    private String startTime;
    /** HH:mm */
    private String endTime;
    private Integer sortOrder;
    /** PROPOSED / ADOPTED / DONE / SKIPPED */
    private String status;
    /** AI / MANUAL */
    private String source;
    private String habitId;
    private String habitName;
    /** 关联习惯当日是否已打卡（反向富化, 块状态不自动联动） */
    private Boolean habitCheckedToday;
    private String subjectId;
    private String subjectName;
    private String chapterId;
    /** 仅 DONE 非空 */
    private LocalDateTime completedAt;

    /** 基础字段映射（富化字段由调用方补设） */
    public static PlanBlockResponse.PlanBlockResponseBuilder baseFrom(PlanBlock block) {
        return PlanBlockResponse.builder()
                .id(block.getId())
                .planId(block.getPlanId())
                .blockType(block.getBlockType())
                .title(block.getTitle())
                .startTime(PlanFreeSlotResponse.fmt(block.getStartTime()))
                .endTime(PlanFreeSlotResponse.fmt(block.getEndTime()))
                .sortOrder(block.getSortOrder())
                .status(block.getStatus())
                .source(block.getSource())
                .habitId(block.getHabitId())
                .subjectId(block.getSubjectId())
                .chapterId(block.getChapterId())
                .completedAt(block.getCompletedAt());
    }

    public static PlanBlockResponse from(PlanBlock block) {
        return baseFrom(block)
                .habitName(null)
                .habitCheckedToday(false)
                .subjectName(null)
                .build();
    }
}
