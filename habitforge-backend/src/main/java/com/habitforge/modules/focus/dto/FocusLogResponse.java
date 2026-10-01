package com.habitforge.modules.focus.dto;

import com.habitforge.modules.focus.entity.FocusLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 某日注意力状态（含达标判定结果，前端不必自己算）
 */
@Data
@Builder
public class FocusLogResponse {

    private LocalDate logDate;

    /** null = 尚未录入 */
    private Integer entertainmentMinutes;

    private Integer pickups;

    private String note;

    private Integer urgeTotal;

    private Integer urgeResisted;

    /** 忍住率百分比；当日无冲动记录时为 null */
    private Integer resistRatePercent;

    /** 当前生效的每日娱乐时长上限（分钟） */
    private Integer limitMinutes;

    /** 已录入且不超上限 */
    private Boolean compliant;

    /** 距上限还剩多少分钟；未录入时为 null */
    private Integer remainMinutes;

    public static FocusLogResponse of(FocusLog log, int limitMinutes) {
        Integer minutes = log == null ? null : log.getEntertainmentMinutes();
        int urgeTotal = log == null || log.getUrgeTotal() == null ? 0 : log.getUrgeTotal();
        int urgeResisted = log == null || log.getUrgeResisted() == null ? 0 : log.getUrgeResisted();
        return FocusLogResponse.builder()
                .logDate(log == null ? null : log.getLogDate())
                .entertainmentMinutes(minutes)
                .pickups(log == null ? null : log.getPickups())
                .note(log == null ? null : log.getNote())
                .urgeTotal(urgeTotal)
                .urgeResisted(urgeResisted)
                .resistRatePercent(urgeTotal == 0 ? null : urgeResisted * 100 / urgeTotal)
                .limitMinutes(limitMinutes)
                .compliant(minutes != null && minutes <= limitMinutes)
                .remainMinutes(minutes == null ? null : limitMinutes - minutes)
                .build();
    }
}
