package com.habitforge.modules.ai.service;

import com.habitforge.modules.ai.dto.PlanResponse;

import java.time.LocalDate;

/**
 * AI 生成今日安排（护栏三件套: 每日限流 6002 / 并发锁 6003 / 全量生成流水）
 */
public interface AiScheduleService {

    /**
     * 生成/重新生成某日安排（同步阻塞调 LLM）。
     * 6001 未启用 / 6006 无空闲时段(不触达 LLM) / 6002 超限 / 6003 并发 / 6004 上游失败(退额度) / 6005 解析失败(不退)
     */
    PlanResponse generate(String userId, LocalDate date);

    /**
     * 每日额度计数的限流 key（**读写两侧必须同源**）。
     * 写入方是本服务的 tryAcquire/release，读取方是 DailyPlanService#getUsage 的展示逻辑；
     * 两边各写一份字符串字面量迟早漂移，表现为「页面说还剩 N 次，后端说已用完」。
     * 静态方法，调用方无需注入本 bean（否则 DailyPlanServiceImpl ↔ AiScheduleServiceImpl 会成环）。
     */
    static String quotaKey(String userId, LocalDate date) {
        return "ai:plan:" + userId + ":" + date;
    }
}
