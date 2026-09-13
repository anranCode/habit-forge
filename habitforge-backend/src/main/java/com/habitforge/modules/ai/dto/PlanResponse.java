package com.habitforge.modules.ai.dto;

import com.habitforge.modules.ai.entity.DailyPlan;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日计划响应（GET /plans/today|/{date}|POST /plans/generate; 字段与前端 types/plan.d.ts 一致）
 */
@Data
@Builder
public class PlanResponse {

    private String id;
    /** yyyy-MM-dd */
    private LocalDate planDate;
    /** 今日已生成次数 */
    private Integer genCount;
    private String lastModel;
    private List<PlanBlockResponse> blocks;
    private List<PlanFreeSlotResponse> freeSlots;

    public static PlanResponse.PlanResponseBuilder baseFrom(DailyPlan plan) {
        return PlanResponse.builder()
                .id(plan.getId())
                .planDate(plan.getPlanDate())
                .genCount(plan.getGenCount() == null ? 0 : plan.getGenCount())
                .lastModel(plan.getLastModel());
    }
}
