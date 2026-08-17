package com.habitforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.modules.checkin.entity.Checkin;
import com.habitforge.modules.checkin.mapper.CheckinMapper;
import com.habitforge.modules.habit.entity.Habit;
import com.habitforge.modules.habit.mapper.HabitMapper;
import com.habitforge.modules.streak.entity.Streak;
import com.habitforge.modules.streak.mapper.StreakMapper;
import com.habitforge.modules.streak.service.impl.StreakServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 习惯链全量重算算法测试（频率感知）
 */
@ExtendWith(MockitoExtension.class)
class StreakServiceImplTest {

    @Mock
    private StreakMapper streakMapper;
    @Mock
    private CheckinMapper checkinMapper;
    @Mock
    private HabitMapper habitMapper;

    @InjectMocks
    private StreakServiceImpl streakService;

    private static final String HABIT_ID = "habit-test";
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
    }

    private Habit habit(String freqType, String freqDays, Integer target) {
        Habit h = new Habit();
        h.setId(HABIT_ID);
        h.setFrequencyType(freqType);
        h.setFrequencyDays(freqDays);
        h.setFrequencyTarget(target);
        return h;
    }

    private void mockCheckins(LocalDate... dates) {
        List<Checkin> list = java.util.Arrays.stream(dates).map(d -> {
            Checkin c = new Checkin();
            c.setHabitId(HABIT_ID);
            c.setCheckDate(d);
            return c;
        }).toList();
        when(checkinMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(list);
    }

    private void mockNoStreakRow() {
        when(streakMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(streakMapper.insert(any(Streak.class))).thenReturn(1);
    }

    // ============ DAILY ============

    @Test
    void daily_consecutiveEndingToday() {
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit("DAILY", null, null));
        mockCheckins(today.minusDays(2), today.minusDays(1), today);
        mockNoStreakRow();

        Streak s = streakService.recalculate(HABIT_ID);
        assertEquals(3, s.getCurrentStreak());
        assertEquals(3, s.getLongestStreak());
    }

    @Test
    void daily_brokenChain_returnsZero() {
        // 最后打卡在前天 -> 昨天漏了 -> 链断
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit("DAILY", null, null));
        mockCheckins(today.minusDays(5), today.minusDays(4), today.minusDays(2));
        mockNoStreakRow();

        Streak s = streakService.recalculate(HABIT_ID);
        assertEquals(0, s.getCurrentStreak());
        assertEquals(2, s.getLongestStreak()); // 5,4 连续两天是最长
    }

    @Test
    void daily_checkedYesterday_stillAlive() {
        // 昨天打了今天还没打 -> 链存活
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit("DAILY", null, null));
        mockCheckins(today.minusDays(2), today.minusDays(1));
        mockNoStreakRow();

        Streak s = streakService.recalculate(HABIT_ID);
        assertEquals(2, s.getCurrentStreak());
    }

    @Test
    void daily_empty() {
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit("DAILY", null, null));
        mockCheckins();
        mockNoStreakRow();

        Streak s = streakService.recalculate(HABIT_ID);
        assertEquals(0, s.getCurrentStreak());
        assertEquals(0, s.getLongestStreak());
    }

    // ============ WEEKLY_DAYS ============

    @Test
    void weeklyDays_countsOnlyScheduledDays() {
        // 每周一三五：从今天往前找最近的已打卡应打卡日
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit("WEEKLY_DAYS", "1,3,5", null));
        // 回填最近 3 个应打卡日（跳过非应打卡日）
        LocalDate d = today;
        java.util.List<LocalDate> scheduled = new java.util.ArrayList<>();
        while (scheduled.size() < 3) {
            int dow = d.getDayOfWeek().getValue();
            if (dow == 1 || dow == 3 || dow == 5) {
                scheduled.add(d);
            }
            d = d.minusDays(1);
        }
        mockCheckins(scheduled.toArray(new LocalDate[0]));
        mockNoStreakRow();

        Streak s = streakService.recalculate(HABIT_ID);
        assertEquals(3, s.getCurrentStreak());
        assertEquals(3, s.getLongestStreak());
    }

    // ============ WEEKLY_COUNT ============

    @Test
    void weeklyCount_targetMetThisWeek() {
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit("WEEKLY_COUNT", null, 2));
        // 本周一天 + 今天 = 2 次达标
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate other = weekStart.equals(today) ? today : weekStart;
        if (other.equals(today)) {
            // 今天就是周一：只能测今天 1 次 < target，链为 0
            mockCheckins(today);
            mockNoStreakRow();
            Streak s = streakService.recalculate(HABIT_ID);
            assertEquals(0, s.getCurrentStreak());
        } else {
            mockCheckins(other, today);
            mockNoStreakRow();
            Streak s = streakService.recalculate(HABIT_ID);
            assertEquals(1, s.getCurrentStreak());
        }
    }

    @Test
    void weeklyCount_previousWeeksChain() {
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit("WEEKLY_COUNT", null, 1));
        // 上上周、上周各达标 1 次，本周未打卡 -> current=2
        LocalDate thisWeek = today.with(java.time.DayOfWeek.MONDAY);
        mockCheckins(thisWeek.minusWeeks(2).plusDays(1), thisWeek.minusWeeks(1).plusDays(1));
        mockNoStreakRow();

        Streak s = streakService.recalculate(HABIT_ID);
        assertEquals(2, s.getCurrentStreak());
        assertEquals(2, s.getLongestStreak());
    }
}
