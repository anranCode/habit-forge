package com.habitforge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 计划空闲时段（PUT 整体覆盖式保存: 事务内先删后插; label 为纯前端快捷标签预设）
 */
@Data
@TableName("plan_free_slots")
public class PlanFreeSlot {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属每日计划 */
    private String planId;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 标签（如: 午休/晚间） */
    private String label;

    /** 排序（越小越靠前） */
    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
