package com.habitforge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日计划（user × day 一天一份 uk_user_date; 懒创建: 首次存空闲时段或生成时建行）
 */
@Data
@TableName("daily_plans")
public class DailyPlan {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** 计划日期（服务端 LocalDate, Asia/Shanghai） */
    private LocalDate planDate;

    /** 当日生成次数（冗余计数, 限流真值在 Redis） */
    private Integer genCount;

    /** 最近一次生成所用模型 */
    private String lastModel;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
