package com.habitforge.modules.review.service;

import com.habitforge.modules.review.dto.WeeklyReportResponse;
import com.habitforge.modules.review.dto.WeeklyReportUpdateRequest;
import com.habitforge.modules.review.dto.WeeklyReportUsageResponse;

import java.time.LocalDate;

/**
 * 复盘（P0 先做 WEEKLY 周报: AI 生成客观评价 + 改进建议, 用户可编辑）
 */
public interface ReviewService {

    /** 某周的周报, 未生成返回 null（anyDayInWeek 落在哪周就取哪周, 周一起算） */
    WeeklyReportResponse getWeek(String userId, LocalDate anyDayInWeek);

    /** 生成本周/某周周报（同步阻塞调 LLM; 6001 未启用 / 8002 本周超限 / 6003 并发 / 6004 上游失败 / 6005 解析失败） */
    WeeklyReportResponse generate(String userId, LocalDate anyDayInWeek);

    /** 编辑周报（AI 只出初稿, 终稿归用户; null 字段不改） */
    WeeklyReportResponse update(String userId, String id, WeeklyReportUpdateRequest request);

    /** 本周生成额度 */
    WeeklyReportUsageResponse usage(String userId, LocalDate anyDayInWeek);

    /**
     * 周报额度的限流 key（**读写两侧必须同源**）。
     * 按「周期起始日」隔离: 周一自然翻篇, 不需要额外的重置逻辑。
     * 静态方法, 调用方无需注入本 bean（避免 ReviewServiceImpl ↔ Controller 成环）。
     */
    static String quotaKey(String userId, LocalDate periodStart) {
        return "ai:report:" + userId + ":" + periodStart;
    }
}
