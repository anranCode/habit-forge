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

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** 目标计划（懒创建失败时可空） */
    private String planId;

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
