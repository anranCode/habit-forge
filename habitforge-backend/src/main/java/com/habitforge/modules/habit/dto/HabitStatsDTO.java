package com.habitforge.modules.habit.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 追踪看板统计数据
 */
@Data
@Builder
public class HabitStatsDTO {

    /** 总习惯数（不含已删除） */
    private Integer totalHabits;
    /** 进行中的习惯数 */
    private Integer activeHabits;
    /** 累计打卡次数 */
    private Integer totalCheckins;
    /** 本月打卡次数 */
    private Integer monthCheckins;
    /** 本月应打卡次数（按各习惯频率推算，截至今天） */
    private Integer monthScheduled;
    /** 本月完成率（0-100，保留一位小数） */
    private Double monthCompletionRate;
    /** 全部习惯中的最长连续纪录 */
    private Integer longestStreakOverall;
    /** 当前最高连续天数 */
    private Integer currentStreakMax;
}
