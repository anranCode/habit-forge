package com.habitforge.common.util;

import com.habitforge.common.enums.FrequencyTypeEnum;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 打卡频率判定工具
 * <p>
 * 约定：周一=1 ... 周日=7（DayOfWeek.getValue）
 * 周区间：以周一为一周的第一天
 */
public final class FrequencyUtil {

    private static final WeekFields WEEK_FIELDS = WeekFields.of(Locale.CHINA);

    private FrequencyUtil() {
    }

    /**
     * 某天是否是该习惯的"应打卡日"
     * DAILY        -> 每天都是
     * WEEKLY_DAYS  -> 仅 frequency_days 指定的星期几
     * WEEKLY_COUNT -> 每天都是候选日（按周统计达标次数）
     */
    public static boolean isScheduledDate(String frequencyType, String frequencyDays, LocalDate date) {
        FrequencyTypeEnum type = FrequencyTypeEnum.fromName(frequencyType);
        return switch (type) {
            case WEEKLY_DAYS -> parseDays(frequencyDays).contains(date.getDayOfWeek().getValue());
            case DAILY, WEEKLY_COUNT -> true;
        };
    }

    /** 解析 "1,3,5" -> {1,3,5} */
    public static Set<Integer> parseDays(String frequencyDays) {
        Set<Integer> days = new HashSet<>();
        if (frequencyDays == null || frequencyDays.isBlank()) {
            return days;
        }
        Arrays.stream(frequencyDays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(s -> {
                    try {
                        int d = Integer.parseInt(s);
                        if (d >= 1 && d <= 7) {
                            days.add(d);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                });
        return days;
    }

    /** 该日期所在周的周一 */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /** 该日期所在周的周日 */
    public static LocalDate weekEnd(LocalDate date) {
        return date.with(DayOfWeek.SUNDAY);
    }

    /** 判断两个日期是否同一周 */
    public static boolean sameWeek(LocalDate a, LocalDate b) {
        return weekStart(a).equals(weekStart(b));
    }
}
