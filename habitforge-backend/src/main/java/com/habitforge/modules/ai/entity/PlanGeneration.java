package com.habitforge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 生成流水（成本审计+排障; 每次生成含失败都落一行, raw_output 留痕）
 */
@Data
@TableName("plan_generations")
public class PlanGeneration {

    /** 今日安排生成 */
    public static final String KIND_PLAN = "PLAN";
    /** 每周复盘报告生成 */
    public static final String KIND_WEEKLY_REPORT = "WEEKLY_REPORT";
    /** 题目考点 AI 识别（P6 接入, ref_id=question_id） */
    public static final String KIND_POINTS = "POINTS";
    /** 主观题 AI 判分（P4 接入, ref_id=question_id） */
    public static final String KIND_GRADE = "GRADE";

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** 目标计划（懒创建失败时可空; 周报流行为 null） */
    private String planId;

    /** 类型: PLAN 今日安排 / WEEKLY_REPORT 周报 / POINTS 考点识别 / GRADE 主观题判分 */
    private String kind;

    /** POINTS/GRADE 流的题目 id; PLAN/WEEKLY_REPORT 行为 null(不加 FK, 题目物理删除后留痕不悬空) */
    private String refId;

    /** 模型名 */
    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 本次生成是否成功 */
    private Boolean success;

    /** 失败原因摘要（≤500 字） */
    private String error;

    /** 模型原始输出（排障留痕） */
    private String rawOutput;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
