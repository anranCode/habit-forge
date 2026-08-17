package com.habitforge.modules.reflection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 习惯心得：每个 Journal × Habit 至多一条
 */
@Data
@TableName("habit_reflections")
public class HabitReflection {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String journalId;

    private String habitId;

    /** 关联打卡记录（未完成时为 null） */
    private String checkinId;

    /** 完成情况: 1完成 0未完成 */
    private Integer result;

    /** 今日感受: 1好 2一般 3糟糕 */
    private Integer feeling;

    /** 难度: 1-5星 */
    private Integer difficulty;

    /** 为什么今天能做到/没做到 */
    private String reason;

    /** 遇到了什么困难 */
    private String obstacle;

    /** 今天学到了什么 */
    private String learning;

    /** 明天准备怎么调整 */
    private String adjustment;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
