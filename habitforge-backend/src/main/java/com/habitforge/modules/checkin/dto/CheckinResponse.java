package com.habitforge.modules.checkin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CheckinResponse {

    @Data
    @Builder
    public static class CheckinInfo {
        private String id;
        private String habitId;
        private LocalDate checkDate;
        private Boolean isCompleted;
        private String note;
    }

    @Data
    @Builder
    public static class StreakInfo {
        private Integer currentStreak;
        private Integer longestStreak;
    }

    private CheckinInfo checkin;
    private StreakInfo streak;
    /** 本次打卡获得的总积分（基础 + 里程碑奖励） */
    private Integer pointsEarned;
    /** 本次新解锁的成就名称 */
    private List<String> newAchievements;
}
