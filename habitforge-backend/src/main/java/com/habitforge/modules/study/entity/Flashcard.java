package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 闪卡（SM-2 间隔重复; subject_id 冗余使到期队列单表零 join）
 */
@Data
@TableName("flashcards")
public class Flashcard {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String subjectId;

    /** 所属章节(null=不挂章节, 删章节 DB 置 NULL 不删卡) */
    private String chapterId;

    /** 卡面(可含 Markdown) */
    private String front;

    /** 卡背(可含 Markdown) */
    private String back;

    /** 难度因子 EF(下限 1.30 / 上限 3.00, 初始 2.50) */
    private BigDecimal easeFactor;

    /** 当前复习间隔(天) */
    private Integer intervalDays;

    /** 连续记得次数(评 1 忘记归零) */
    private Integer repetition;

    /** 累计忘记次数 */
    private Integer lapses;

    /** 到期日(初始=创建日) */
    private LocalDate dueDate;

    private LocalDateTime lastReviewedAt;

    /** ACTIVE / SUSPENDED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
