package com.habitforge.modules.review.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 本周报告生成额度（与 /plans/usage 同构）
 */
@Data
@Builder
public class WeeklyReportUsageResponse {

    private LocalDate periodStart;
    private LocalDate periodEnd;
    /** 本周已生成次数 */
    private Integer used;
    /** 本周上限 */
    private Integer limit;
    private Integer remaining;
    /** AI 总开关（false 时前端应隐藏生成按钮） */
    private Boolean enabled;
}
