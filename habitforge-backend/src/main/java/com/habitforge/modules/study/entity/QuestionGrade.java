package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 主观题 AI 判分留痕（每次判分一行, 含失败无行 —— 失败痕迹在 plan_generations 流水）。
 * created_at/updated_at 由 DB 默认值维护（MP 对 null 字段跳过 insert, 沿 Question 惯例）。
 */
@Data
@TableName("question_grades")
public class QuestionGrade {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String questionId;

    /** 学生作答全文（服务层截断后落库, ≤2000） */
    private String userAnswer;

    /** 判定结果 —— 服务端按 SHORT_GRADE_PASS_RATIO 换算, 非模型输出 */
    private Boolean correct;

    /** AI 要点拆解 JSON: [{"point":"...","hit":true}]（仿 options 惯例, 服务层 hutool 序列化） */
    private String points;

    private Integer covered;

    private Integer total;

    /** 覆盖率 covered/total（审计冗余; 判定不读它） */
    private Double ratio;

    private String model;

    private Integer totalTokens;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
