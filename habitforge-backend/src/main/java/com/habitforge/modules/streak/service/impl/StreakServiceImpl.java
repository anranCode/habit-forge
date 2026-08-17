package com.habitforge.modules.streak.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.enums.FrequencyTypeEnum;
import com.habitforge.common.util.FrequencyUtil;
import com.habitforge.modules.checkin.entity.Checkin;
import com.habitforge.modules.checkin.mapper.CheckinMapper;
import com.habitforge.modules.habit.entity.Habit;
import com.habitforge.modules.habit.mapper.HabitMapper;
import com.habitforge.modules.streak.entity.Streak;
import com.habitforge.modules.streak.mapper.StreakMapper;
import com.habitforge.modules.streak.service.StreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 习惯链计算：全量重算策略。
 * <p>
 * 单用户场景数据量小，每次打卡/撤销后从全部打卡记录重算，
 * 避免增量算法在撤销、漏卡、周频率下的边界 bug。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreakServiceImpl implements StreakService {

    private final StreakMapper streakMapper;
    private final CheckinMapper checkinMapper;
    private final HabitMapper habitMapper;

    @Override
    public Streak getByHabitId(String habitId) {
        Streak streak = streakMapper.selectOne(new LambdaQueryWrapper<Streak>()
                .eq(Streak::getHabitId, habitId));
        if (streak == null) {
            streak = new Streak();
            streak.setHabitId(habitId);
            streak.setCurrentStreak(0);
            streak.setLongestStreak(0);
        }
        return streak;
    }

    @Override
    public Streak recalculate(String habitId) {
        Habit habit = habitMapper.selectById(habitId);
        List<LocalDate> dates = checkinMapper.selectList(new LambdaQueryWrapper<Checkin>()
                        .eq(Checkin::getHabitId, habitId)
                        .orderByAsc(Checkin::getCheckDate))
                .stream()
                .map(Checkin::getCheckDate)
                .distinct()
                .sorted()
                .toList();

        LocalDate today = LocalDate.now();
        int current;
        int longest;

        if (habit == null) {
            current = 0;
            longest = 0;
        } else {
            FrequencyTypeEnum type = FrequencyTypeEnum.fromName(habit.getFrequencyType());
            int[] result = switch (type) {
                case DAILY -> calcDaily(dates, today);
                case WEEKLY_DAYS -> calcWeeklyDays(dates, habit.getFrequencyDays(), today);
                case WEEKLY_COUNT -> calcWeeklyCount(dates, habit.getFrequencyTarget(), today);
            };
            current = result[0];
            longest = result[1];
        }

        // upsert
        Streak streak = streakMapper.selectOne(new LambdaQueryWrapper<Streak>()
                .eq(Streak::getHabitId, habitId));
        if (streak == null) {
            streak = new Streak();
            streak.setHabitId(habitId);
        }
        streak.setCurrentStreak(current);
        streak.setLongestStreak(longest);
        streak.setLastCheckDate(dates.isEmpty() ? null : dates.get(dates.size() - 1));
        if (streak.getId() == null) {
            streakMapper.insert(streak);
        } else {
            streakMapper.updateById(streak);
        }
        log.debug("重算习惯链 habitId={}, current={}, longest={}", habitId, current, longest);
        return streak;
    }

    /**
     * DAILY：连续自然日
     * @return [current, longest]
     */
    private int[] calcDaily(List<LocalDate> dates, LocalDate today) {
        if (dates.isEmpty()) {
            return new int[]{0, 0};
        }
        Set<LocalDate> set = new HashSet<>(dates);
        // longest：顺序扫描连续段
        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = d;
        }
        // current：末次打卡必须是今天或昨天，链才算存活
        LocalDate last = dates.get(dates.size() - 1);
        int current = 0;
        if (!last.isBefore(today.minusDays(1))) {
            current = 1;
            LocalDate cur = last;
            while (set.contains(cur.minusDays(1))) {
                current++;
                cur = cur.minusDays(1);
            }
        }
        return new int[]{current, longest};
    }

    /**
     * WEEKLY_DAYS：只数指定星期几；漏掉一个应打卡日即断链。
     * 今天若应打卡但还没打，不算断（当天还没结束）。
     * @return [current, longest]
     */
    private int[] calcWeeklyDays(List<LocalDate> dates, String frequencyDays, LocalDate today) {
        if (dates.isEmpty()) {
            return new int[]{0, 0};
        }
        Set<LocalDate> checked = new HashSet<>(dates);
        LocalDate first = dates.get(0);

        // longest：从第一次打卡日顺推到今天
        int longest = 0;
        int run = 0;
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            if (!FrequencyUtil.isScheduledDate(FrequencyTypeEnum.WEEKLY_DAYS.name(), frequencyDays, d)) {
                continue;
            }
            if (checked.contains(d)) {
                run++;
                longest = Math.max(longest, run);
            } else if (d.equals(today)) {
                // 今天还没打，暂不断链
            } else {
                run = 0;
            }
        }

        // current：从今天往回数已打卡的应打卡日
        int current = 0;
        if (FrequencyUtil.isScheduledDate(FrequencyTypeEnum.WEEKLY_DAYS.name(), frequencyDays, today)) {
            if (checked.contains(today)) {
                current++;
            }
        }
        for (LocalDate d = today.minusDays(1); !d.isBefore(first); d = d.minusDays(1)) {
            if (!FrequencyUtil.isScheduledDate(FrequencyTypeEnum.WEEKLY_DAYS.name(), frequencyDays, d)) {
                continue;
            }
            if (checked.contains(d)) {
                current++;
            } else {
                break;
            }
        }
        return new int[]{current, longest};
    }

    /**
     * WEEKLY_COUNT：按周统计，一周达标 N 次记 1 链。
     * 本周尚未达标且未结束时不断链。
     * @return [current, longest]
     */
    private int[] calcWeeklyCount(List<LocalDate> dates, Integer frequencyTarget, LocalDate today) {
        if (dates.isEmpty()) {
            return new int[]{0, 0};
        }
        int target = (frequencyTarget == null || frequencyTarget < 1) ? 1 : frequencyTarget;
        Map<LocalDate, Long> perWeek = dates.stream()
                .collect(Collectors.groupingBy(FrequencyUtil::weekStart, Collectors.counting()));

        LocalDate firstWeek = FrequencyUtil.weekStart(dates.get(0));
        LocalDate thisWeek = FrequencyUtil.weekStart(today);

        // longest：顺序扫描各周
        int longest = 0;
        int run = 0;
        for (LocalDate w = firstWeek; !w.isAfter(thisWeek); w = w.plusWeeks(1)) {
            long count = perWeek.getOrDefault(w, 0L);
            if (count >= target) {
                run++;
                longest = Math.max(longest, run);
            } else if (w.equals(thisWeek)) {
                // 本周未结束，暂不断链
            } else {
                run = 0;
            }
        }

        // current：本周已达标则计入，再往回数连续达标的周
        int current = 0;
        if (perWeek.getOrDefault(thisWeek, 0L) >= target) {
            current++;
        }
        for (LocalDate w = thisWeek.minusWeeks(1); !w.isBefore(firstWeek); w = w.minusWeeks(1)) {
            if (perWeek.getOrDefault(w, 0L) >= target) {
                current++;
            } else {
                break;
            }
        }
        return new int[]{current, longest};
    }

    @Override
    public List<Map<String, Object>> topStreaks(String userId, int limit) {
        List<Habit> habits = habitMapper.selectList(new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId)
                .eq(Habit::getIsActive, 1));
        if (habits.isEmpty()) {
            return List.of();
        }
        Map<String, Habit> habitMap = habits.stream()
                .collect(Collectors.toMap(Habit::getId, h -> h));
        List<Streak> streaks = streakMapper.selectList(new LambdaQueryWrapper<Streak>()
                .in(Streak::getHabitId, habitMap.keySet())
                .orderByDesc(Streak::getCurrentStreak)
                .last("LIMIT " + Math.max(1, limit)));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Streak s : streaks) {
            Habit h = habitMap.get(s.getHabitId());
            if (h == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("habitId", h.getId());
            item.put("name", h.getName());
            item.put("identityTag", h.getIdentityTag());
            item.put("category", h.getCategory());
            item.put("frequencyType", h.getFrequencyType());
            item.put("currentStreak", s.getCurrentStreak());
            item.put("longestStreak", s.getLongestStreak());
            item.put("lastCheckDate", s.getLastCheckDate());
            result.add(item);
        }
        return result;
    }
}
