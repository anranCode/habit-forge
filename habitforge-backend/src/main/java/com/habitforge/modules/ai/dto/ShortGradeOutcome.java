package com.habitforge.modules.ai.dto;

import java.util.List;

/**
 * 主观题判分结果（ai 域内部形状; 由 study 域判分编排映射进 GradeResponse.AiGrade,
 * 避免 ai 模块反向依赖 study 的响应 DTO）。
 *
 * @param points  规范化后的要点清单（解析层已去畸形/去重/截断）
 * @param covered 命中要点数
 * @param total   要点总数
 * @param ratio   覆盖率, 两位小数, 仅展示/审计用
 * @param correct 服务端换算: covered >= ceil(total * SHORT_GRADE_PASS_RATIO)
 * @param gradeId question_grades 行 id（留痕回查）
 */
public record ShortGradeOutcome(
        List<ShortGradeDraft.PointDraft> points,
        int covered,
        int total,
        double ratio,
        boolean correct,
        String gradeId) {
}
