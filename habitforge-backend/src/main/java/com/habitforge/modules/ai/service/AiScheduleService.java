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
}
