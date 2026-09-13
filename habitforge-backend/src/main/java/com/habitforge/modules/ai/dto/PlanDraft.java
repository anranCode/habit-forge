package com.habitforge.modules.ai.dto;

import java.util.List;

/**
 * LLM 结构化输出草稿（BeanOutputConverter 目标类型; 字段即 prompt schema）
 * 时间格式 HH:mm; habitId/subjectId/chapterId 只能来自上下文清单, 否则 null
 */
public record PlanDraft(List<PlanDraftBlock> blocks) {

    public record PlanDraftBlock(
            String start,
            String end,
            String title,
            String type,
            String habitId,
            String subjectId,
            String chapterId) {
    }
}
