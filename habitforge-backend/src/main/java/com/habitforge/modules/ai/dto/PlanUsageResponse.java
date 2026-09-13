package com.habitforge.modules.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 今日生成用量（GET /plans/usage → {used, remaining, todayTokens}）
 */
@Data
@Builder
public class PlanUsageResponse {

    /** 今日已生成次数（daily_plans.gen_count 冗余真源） */
    private Integer used;
    /** 剩余次数（max(0, limit - used)） */
    private Integer remaining;
    /** 今日累计 token 消耗（plan_generations 当日 SUM） */
    private Long todayTokens;
}
