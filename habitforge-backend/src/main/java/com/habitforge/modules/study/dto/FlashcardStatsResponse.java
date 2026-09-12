package com.habitforge.modules.study.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 闪卡统计（复习入口页数据源）
 */
@Data
@Builder
public class FlashcardStatsResponse {

    /** 今日到期数 */
    private Integer dueToday;
    /** 今日已复习数 */
    private Integer reviewedToday;
    /** ACTIVE 卡总数 */
    private Integer total;
    /** 未来 7 天(today+1 ~ today+7)每日到期数, 无到期日补 0 */
    private List<DayCount> next7Days;

    @Data
    @Builder
    public static class DayCount {
        private LocalDate date;
        private Integer count;
    }
}
