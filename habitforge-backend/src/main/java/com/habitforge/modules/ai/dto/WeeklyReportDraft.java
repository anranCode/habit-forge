package com.habitforge.modules.ai.dto;

import java.util.List;

/**
 * LLM 结构化输出草稿（BeanOutputConverter 目标类型; 字段即 prompt schema）
 * 只允许基于上下文中给出的客观数据下结论, 不允许编造未提供的数字
 */
public record WeeklyReportDraft(
        Integer score,
        String title,
        String goodThings,
        String badThings,
        String learnings,
        List<String> suggestions) {
}
