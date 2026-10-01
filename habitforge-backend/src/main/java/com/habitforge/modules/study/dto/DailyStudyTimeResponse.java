package com.habitforge.modules.study.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 区间内每日学习时长（周报与趋势图数据源; 无记录的日子补 0, 前端不必再补洞）
 */
@Data
@Builder
public class DailyStudyTimeResponse {

    private LocalDate date;
    private Integer minutes;
    private Integer sessionCount;
}
