package com.habitforge.modules.habit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("habits")
public class Habit {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String name;

    /** 身份标签(如: 读者,跑步者) */
    private String identityTag;

    /** HEALTH/LEARNING/WORK/LIFE/OTHER */
    private String category;

    /** GOOD/BAD(预留) */
    private String habitType;

    /** DAILY / WEEKLY_DAYS / WEEKLY_COUNT */
    private String frequencyType;

    /** WEEKLY_DAYS 时每周哪几天，如 "1,3,5"（1=周一） */
    private String frequencyDays;

    /** WEEKLY_COUNT 时每周目标次数 */
    private Integer frequencyTarget;

    /** 两分钟微习惯版本 */
    private String twoMinuteVersion;

    /** 执行时间，如 07:00 */
    private String execTime;

    /** 执行地点，如 客厅 */
    private String execPlace;

    /** 习惯叠加：继[某习惯]之后 */
    private String stackAfter;

    private Integer isActive;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    private Integer priority;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
