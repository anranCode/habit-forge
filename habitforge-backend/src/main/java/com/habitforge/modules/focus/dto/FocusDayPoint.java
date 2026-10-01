package com.habitforge.modules.focus.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 单日注意力数据点（趋势图与周报共用）
 */
@Data
@Builder
public class FocusDayPoint {

    private LocalDate date;

    /** null = 当日未录入（前端画成断点，不是 0） */
    private Integer entertainmentMinutes;

    /** 已录入且不超上限 */
    private Boolean compliant;

    private Integer urgeTotal;

    private Integer urgeResisted;
}
