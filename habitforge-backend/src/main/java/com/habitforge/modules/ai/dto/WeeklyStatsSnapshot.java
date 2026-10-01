package com.habitforge.modules.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 周报客观数据快照（同源两用: ①拼进 prompt 供 AI 分析 ②原样存 reviews.stats_snapshot 供审计）
 *
 * <p>日期一律用字符串, 避免 JSON 序列化对 LocalDate 的格式分歧。
 */
@Data
@Builder
public class WeeklyStatsSnapshot {

    private String periodStart;
    private String periodEnd;
    private StudyStats study;
    private HabitStats habits;
    private CardStats cards;
    private WrongStats wrongs;
    private List<SubjectProgress> subjects;
    private JournalStats journals;
    private ReflectionStats reflections;
    /** 手机节制（P2）: 没有注意力记录的周该节整体为空 */
    private FocusStats focus;

    /** 学习时长 */
    @Data
    @Builder
    public static class StudyStats {
        private Integer totalMinutes;
        /** 日均分钟（按周期总天数算, 没学的日子按 0 计入） */
        private Integer avgMinutesPerDay;
        /** 有学习记录的天数 */
        private Integer activeDays;
        private Integer sessionCount;
        private List<DailyPoint> daily;
        private List<NameMinutes> bySubject;
    }

    @Data
    @Builder
    public static class DailyPoint {
        private String date;
        private Integer minutes;
    }

    @Data
    @Builder
    public static class NameMinutes {
        private String name;
        private Integer minutes;
    }

    /** 习惯打卡 */
    @Data
    @Builder
    public static class HabitStats {
        private Integer activeHabitCount;
        private Integer checkinTotal;
        /** 本周至少打卡一次的习惯占比 */
        private Integer habitHitRatePercent;
        private List<HabitItem> items;
    }

    @Data
    @Builder
    public static class HabitItem {
        private String name;
        private String category;
        private String habitType;
        /** 本周打卡天数 */
        private Integer weekCheckedDays;
        private Integer currentStreak;
        private Integer longestStreak;
        private Boolean missedYesterday;
    }

    /** 闪卡复习 */
    @Data
    @Builder
    public static class CardStats {
        private Integer reviewedTotal;
        private Integer reviewedDays;
        /** 评分 ≥3（记得/轻松）占比 */
        private Integer rememberRatePercent;
        private Integer dueNow;
    }

    /** 错题 */
    @Data
    @Builder
    public static class WrongStats {
        private Integer pendingNow;
        private Integer masteredThisWeek;
    }

    /** 科目进度（含本周投入时长） */
    @Data
    @Builder
    public static class SubjectProgress {
        private String name;
        private Integer chapterDone;
        private Integer chapterTotal;
        private Integer daysLeft;
        private Integer weekMinutes;
    }

    /** 日记与心情 */
    @Data
    @Builder
    public static class JournalStats {
        private Integer count;
        private Integer moodGood;
        private Integer moodNormal;
        private Integer moodTired;
    }

    /** 习惯心得 */
    @Data
    @Builder
    public static class ReflectionStats {
        private Integer count;
        private Integer doneCount;
        private Integer undoneCount;
    }

    /** 手机节制（P2 注意力日志） */
    @Data
    @Builder
    public static class FocusStats {
        /** 当周生效的每日娱乐时长上限（分钟） */
        private Integer limitMinutes;
        /** 有录入时长的天数 */
        private Integer recordedDays;
        /** 娱乐时长不超上限的天数 */
        private Integer compliantDays;
        /** 已录入天数的平均娱乐时长；未录入时为 null */
        private Integer avgEntertainmentMinutes;
        private Integer totalEntertainmentMinutes;
        /** 冲动（想刷手机）总次数 */
        private Integer urgeTotal;
        /** 其中忍住的次数 */
        private Integer urgeResisted;
        /** 忍住率百分比；无冲动记录时为 null */
        private Integer resistRatePercent;
    }
}
