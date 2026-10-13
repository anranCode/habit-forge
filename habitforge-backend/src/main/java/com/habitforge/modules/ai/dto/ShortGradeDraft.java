package com.habitforge.modules.ai.dto;

import java.util.List;

/**
 * 主观题判分 LLM 输出草稿（BeanOutputConverter 目标类型; 字段即 prompt schema）。
 * 模型只拆要点、逐点判 hit —— 刻意不设 correct 字段: 对错由服务端按 SHORT_GRADE_PASS_RATIO
 * 从 hit 计数换算（计划决策 6, 消除"AI 结论与自己要点列表打架"的通道）。
 */
public record ShortGradeDraft(List<PointDraft> points) {

    /** hit 用包装类型: 模型漏字段 → null, 解析层按坏条目丢弃, 不当 false 蒙判 */
    public record PointDraft(String point, Boolean hit) {
    }
}
