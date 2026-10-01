package com.habitforge.modules.focus.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 注意力趋势（区间内每日 + 汇总）
 */
@Data
@Builder
public class FocusTrendResponse {

    private LocalDate from;
    private LocalDate to;
    private Integer limitMinutes;

    /** 区间总天数 */
    private Integer totalDays;
    /** 有录入时长的天数（分母用这个算日均，没录入的日子不该拉低平均） */
    private Integer recordedDays;
    /** 达标天数 */
    private Integer compliantDays;
    /** 已录入天数的平均娱乐时长（分钟） */
    private Integer avgEntertainmentMinutes;
    private Integer totalEntertainmentMinutes;
    private Integer urgeTotal;
    private Integer urgeResisted;
    /** 区间忍住率；无冲动记录时为 null */
    private Integer resistRatePercent;

    /** 每日明细（含未录入的日子, minutes 为 null） */
    private List<FocusDayPoint> days;
}
