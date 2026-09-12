package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习科目（自考科目; 逻辑删除防误删连带笔记/题目）
 */
@Data
@TableName("subjects")
public class Subject {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String name;

    /** 考试日期(倒计时用, null=无考期) */
    private LocalDate examDate;

    /** 考试学期, 如 2026上半年 */
    private String examSession;

    private String description;

    /** 排序(越小越靠前) */
    private Integer sortOrder;

    private Integer isActive;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
