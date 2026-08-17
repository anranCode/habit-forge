package com.habitforge.modules.habit.dto;

import com.habitforge.modules.habit.entity.Habit;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HabitResponseDTO {

    private String id;
    private String name;
    private String identityTag;
    private String category;
    private String habitType;
    private String frequencyType;
    private String frequencyDays;
    private Integer frequencyTarget;
    private String twoMinuteVersion;
    private String execTime;
    private String execPlace;
    private String stackAfter;
    private Boolean isActive;
    private Integer priority;
    private LocalDateTime createdAt;

    /** 当前连续天数（列表接口附带） */
    private Integer currentStreak;
    /** 最长连续天数（列表接口附带） */
    private Integer longestStreak;
    /** 今日是否已打卡（列表接口附带） */
    private Boolean checkedToday;
    /** 昨天（应打卡日）是否漏卡 -> "绝不错过两次"提醒 */
    private Boolean missedYesterday;
    /** WEEKLY_COUNT 习惯：本周已打卡次数 */
    private Integer weekCheckedCount;

    public static HabitResponseDTO from(Habit habit) {
        return HabitResponseDTO.builder()
                .id(habit.getId())
                .name(habit.getName())
                .identityTag(habit.getIdentityTag())
                .category(habit.getCategory())
                .habitType(habit.getHabitType())
                .frequencyType(habit.getFrequencyType())
                .frequencyDays(habit.getFrequencyDays())
                .frequencyTarget(habit.getFrequencyTarget())
                .twoMinuteVersion(habit.getTwoMinuteVersion())
                .execTime(habit.getExecTime())
                .execPlace(habit.getExecPlace())
                .stackAfter(habit.getStackAfter())
                .isActive(habit.getIsActive() != null && habit.getIsActive() == 1)
                .priority(habit.getPriority())
                .createdAt(habit.getCreatedAt())
                .build();
    }
}
