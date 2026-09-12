package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题（uk_user_question 一人一题一行; 摘除只置 mastered=1 不删行）
 */
@Data
@TableName("wrong_questions")
public class WrongQuestion {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String questionId;

    /** 累计答错次数 */
    private Integer wrongCount;

    /** 连对次数(达 WRONG_MASTER_STREAK 自动摘除) */
    private Integer correctStreak;

    /** 0未摘除 / 1已摘除 */
    private Integer mastered;

    private LocalDateTime lastWrongAt;

    private LocalDateTime lastPracticedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
