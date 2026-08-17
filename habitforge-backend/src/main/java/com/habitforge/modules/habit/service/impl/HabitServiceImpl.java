package com.habitforge.modules.habit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.enums.FrequencyTypeEnum;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.FrequencyUtil;
import com.habitforge.modules.checkin.entity.Checkin;
import com.habitforge.modules.checkin.mapper.CheckinMapper;
import com.habitforge.modules.habit.dto.*;
import com.habitforge.modules.habit.entity.Habit;
import com.habitforge.modules.habit.mapper.HabitMapper;
import com.habitforge.modules.habit.service.HabitService;
import com.habitforge.modules.streak.entity.Streak;
import com.habitforge.modules.streak.mapper.StreakMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HabitServiceImpl implements HabitService {

    private final HabitMapper habitMapper;
    private final StreakMapper streakMapper;
    private final CheckinMapper checkinMapper;

    @Override
    public HabitResponseDTO create(String userId, HabitCreateDTO dto) {
        validateFrequency(dto.getFrequencyType(), dto.getFrequencyDays(), dto.getFrequencyTarget());

        Habit habit = new Habit();
        habit.setUserId(userId);
        habit.setName(dto.getName());
        habit.setIdentityTag(dto.getIdentityTag());
        habit.setCategory(orOther(dto.getCategory()));
        habit.setHabitType("GOOD");
        habit.setFrequencyType(dto.getFrequencyType() == null ? FrequencyTypeEnum.DAILY.name() : dto.getFrequencyType());
        habit.setFrequencyDays(dto.getFrequencyDays());
        habit.setFrequencyTarget(dto.getFrequencyTarget() == null ? 1 : dto.getFrequencyTarget());
        habit.setTwoMinuteVersion(dto.getTwoMinuteVersion());
        habit.setExecTime(dto.getExecTime());
        habit.setExecPlace(dto.getExecPlace());
        habit.setStackAfter(dto.getStackAfter());
        habit.setIsActive(1);
        habit.setDeleted(0);
        habit.setPriority(0);
        habitMapper.insert(habit);

        // 初始化空链
        Streak streak = new Streak();
        streak.setHabitId(habit.getId());
        streak.setCurrentStreak(0);
        streak.setLongestStreak(0);
        streakMapper.insert(streak);

        log.info("创建习惯 user={}, name={}", userId, habit.getName());
        return HabitResponseDTO.from(habit);
    }

    @Override
    public HabitResponseDTO update(String userId, String habitId, HabitUpdateDTO dto) {
        Habit habit = loadOwned(userId, habitId);
        validateFrequency(dto.getFrequencyType(), dto.getFrequencyDays(), dto.getFrequencyTarget());

        if (dto.getName() != null && !dto.getName().isBlank()) {
            habit.setName(dto.getName());
        }
        if (dto.getIdentityTag() != null) {
            habit.setIdentityTag(dto.getIdentityTag());
        }
        if (dto.getCategory() != null && !dto.getCategory().isBlank()) {
            habit.setCategory(orOther(dto.getCategory()));
        }
        if (dto.getFrequencyType() != null && !dto.getFrequencyType().isBlank()) {
            habit.setFrequencyType(dto.getFrequencyType());
        }
        if (dto.getFrequencyDays() != null) {
            habit.setFrequencyDays(dto.getFrequencyDays());
        }
        if (dto.getFrequencyTarget() != null) {
            habit.setFrequencyTarget(dto.getFrequencyTarget());
        }
        if (dto.getTwoMinuteVersion() != null) {
            habit.setTwoMinuteVersion(dto.getTwoMinuteVersion());
        }
        if (dto.getExecTime() != null) {
            habit.setExecTime(dto.getExecTime());
        }
        if (dto.getExecPlace() != null) {
            habit.setExecPlace(dto.getExecPlace());
        }
        if (dto.getStackAfter() != null) {
            habit.setStackAfter(dto.getStackAfter());
        }
        if (dto.getIsActive() != null) {
            habit.setIsActive(dto.getIsActive());
        }
        if (dto.getPriority() != null) {
            habit.setPriority(dto.getPriority());
        }
        habitMapper.updateById(habit);
        return HabitResponseDTO.from(habit);
    }

    @Override
    public void delete(String userId, String habitId) {
        Habit habit = loadOwned(userId, habitId);
        habitMapper.deleteById(habit.getId()); // @TableLogic 逻辑删除
        log.info("删除习惯 user={}, habit={}", userId, habit.getName());
    }

    @Override
    public HabitResponseDTO getDetail(String userId, String habitId) {
        Habit habit = loadOwned(userId, habitId);
        HabitResponseDTO dto = HabitResponseDTO.from(habit);
        enrich(userId, List.of(dto), habitMapOf(habit));
        return dto;
    }

    @Override
    public List<HabitResponseDTO> listMine(String userId, Boolean activeOnly) {
        LambdaQueryWrapper<Habit> qw = new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId)
                .orderByDesc(Habit::getPriority)
                .orderByAsc(Habit::getCreatedAt);
        if (Boolean.TRUE.equals(activeOnly)) {
            qw.eq(Habit::getIsActive, 1);
        }
        List<Habit> habits = habitMapper.selectList(qw);
        List<HabitResponseDTO> dtos = habits.stream().map(HabitResponseDTO::from).toList();
        enrich(userId, dtos, habitMapOf(habits.toArray(new Habit[0])));
        return dtos;
    }

    @Override
    public List<HabitResponseDTO> listToday(String userId) {
        LocalDate today = LocalDate.now();
        List<Habit> habits = habitMapper.selectList(new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId)
                .eq(Habit::getIsActive, 1)
                .orderByDesc(Habit::getPriority)
                .orderByAsc(Habit::getExecTime));
        List<Habit> scheduled = habits.stream()
                .filter(h -> FrequencyUtil.isScheduledDate(h.getFrequencyType(), h.getFrequencyDays(), today))
                .toList();
        List<HabitResponseDTO> dtos = scheduled.stream().map(HabitResponseDTO::from).toList();
        enrich(userId, dtos, habitMapOf(scheduled.toArray(new Habit[0])));
        return dtos;
    }

    @Override
    public HabitStatsDTO getStats(String userId) {
        LocalDate today = LocalDate.now();
        List<Habit> habits = habitMapper.selectList(new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId));
        List<String> habitIds = habits.stream().map(Habit::getId).toList();

        List<Checkin> allCheckins = habitIds.isEmpty() ? List.of()
                : checkinMapper.selectList(new LambdaQueryWrapper<Checkin>()
                .in(Checkin::getHabitId, habitIds)
                .select(Checkin::getCheckDate, Checkin::getHabitId));

        YearMonth thisMonth = YearMonth.from(today);
        long monthCheckins = allCheckins.stream()
                .filter(c -> YearMonth.from(c.getCheckDate()).equals(thisMonth))
                .count();

        // 应打卡数：各习惯从 max(创建日, 本月1日) 到今天的应打卡日之和
        LocalDate monthStart = thisMonth.atDay(1);
        int monthScheduled = 0;
        for (Habit h : habits) {
            if (h.getIsActive() != null && h.getIsActive() == 0) {
                continue;
            }
            LocalDate start = h.getCreatedAt() != null && h.getCreatedAt().toLocalDate().isAfter(monthStart)
                    ? h.getCreatedAt().toLocalDate() : monthStart;
            monthScheduled += countScheduled(h, start, today);
        }
        double rate = monthScheduled == 0 ? 0.0
                : Math.min(100.0, Math.round(monthCheckins * 1000.0 / monthScheduled) / 10.0);

        List<Streak> streaks = habitIds.isEmpty() ? List.of()
                : streakMapper.selectList(new LambdaQueryWrapper<Streak>().in(Streak::getHabitId, habitIds));
        int longestOverall = streaks.stream().mapToInt(s -> s.getLongestStreak() == null ? 0 : s.getLongestStreak()).max().orElse(0);
        int currentMax = streaks.stream().mapToInt(s -> s.getCurrentStreak() == null ? 0 : s.getCurrentStreak()).max().orElse(0);

        return HabitStatsDTO.builder()
                .totalHabits(habits.size())
                .activeHabits((int) habits.stream().filter(h -> h.getIsActive() != null && h.getIsActive() == 1).count())
                .totalCheckins(allCheckins.size())
                .monthCheckins((int) monthCheckins)
                .monthScheduled(monthScheduled)
                .monthCompletionRate(rate)
                .longestStreakOverall(longestOverall)
                .currentStreakMax(currentMax)
                .build();
    }

    // ================= 私有方法 =================

    private Habit loadOwned(String userId, String habitId) {
        Habit habit = habitMapper.selectById(habitId);
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HABIT_NOT_FOUND);
        }
        return habit;
    }

    private Map<String, Habit> habitMapOf(Habit... habits) {
        return Arrays.stream(habits).collect(Collectors.toMap(Habit::getId, h -> h));
    }

    private String orOther(String category) {
        return (category == null || category.isBlank()) ? "OTHER" : category;
    }

    private void validateFrequency(String type, String days, Integer target) {
        if (type == null || type.isBlank()) {
            return;
        }
        FrequencyTypeEnum t = FrequencyTypeEnum.fromName(type);
        if (t == FrequencyTypeEnum.WEEKLY_DAYS) {
            if (days == null || FrequencyUtil.parseDays(days).isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "每周指定几天模式需选择至少一个星期几");
            }
        }
        if (t == FrequencyTypeEnum.WEEKLY_COUNT && (target == null || target < 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每周N次模式需设置目标次数（≥1）");
        }
    }

    /**
     * 为列表附带：链信息、今日打卡状态、昨日漏卡提醒、本周进度
     */
    private void enrich(String userId, List<HabitResponseDTO> dtos, Map<String, Habit> habitMap) {
        if (dtos.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        List<String> ids = dtos.stream().map(HabitResponseDTO::getId).toList();

        Map<String, Streak> streakMap = streakMapper.selectList(new LambdaQueryWrapper<Streak>()
                        .in(Streak::getHabitId, ids))
                .stream().collect(Collectors.toMap(Streak::getHabitId, s -> s));

        // 今天 + 昨天 + 本周的打卡记录一次查出
        LocalDate weekStart = FrequencyUtil.weekStart(today);
        List<Checkin> recent = checkinMapper.selectList(new LambdaQueryWrapper<Checkin>()
                .in(Checkin::getHabitId, ids)
                .ge(Checkin::getCheckDate, weekStart.isBefore(yesterday) ? weekStart : yesterday)
                .select(Checkin::getHabitId, Checkin::getCheckDate));
        Map<String, Set<LocalDate>> recentMap = recent.stream().collect(Collectors.groupingBy(
                Checkin::getHabitId,
                Collectors.mapping(Checkin::getCheckDate, Collectors.toSet())));

        for (HabitResponseDTO dto : dtos) {
            Streak s = streakMap.get(dto.getId());
            dto.setCurrentStreak(s == null || s.getCurrentStreak() == null ? 0 : s.getCurrentStreak());
            dto.setLongestStreak(s == null || s.getLongestStreak() == null ? 0 : s.getLongestStreak());

            Set<LocalDate> dates = recentMap.getOrDefault(dto.getId(), Set.of());
            dto.setCheckedToday(dates.contains(today));

            Habit h = habitMap.get(dto.getId());
            boolean missed = false;
            // 漏卡判定仅对"昨天已存在"的习惯生效：
            // 习惯是今天才创建的（如注册当天新建），昨天它还不存在，不能算漏卡
            boolean existedYesterday = h != null && h.getCreatedAt() != null
                    && !h.getCreatedAt().toLocalDate().isAfter(yesterday);
            if (existedYesterday && !"WEEKLY_COUNT".equals(h.getFrequencyType())
                    && FrequencyUtil.isScheduledDate(h.getFrequencyType(), h.getFrequencyDays(), yesterday)) {
                missed = !dates.contains(yesterday);
            }
            dto.setMissedYesterday(missed);

            if (h != null && "WEEKLY_COUNT".equals(h.getFrequencyType())) {
                dto.setWeekCheckedCount((int) dates.stream()
                        .filter(d -> !d.isBefore(weekStart) && !d.isAfter(today)).count());
            }
        }
    }

    /** 统计习惯在 [start, end] 内的应打卡日数量 */
    private int countScheduled(Habit h, LocalDate start, LocalDate end) {
        FrequencyTypeEnum type = FrequencyTypeEnum.fromName(h.getFrequencyType());
        return switch (type) {
            case DAILY -> (int) (end.toEpochDay() - start.toEpochDay() + 1);
            case WEEKLY_DAYS -> {
                int count = 0;
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    if (FrequencyUtil.isScheduledDate(h.getFrequencyType(), h.getFrequencyDays(), d)) {
                        count++;
                    }
                }
                yield count;
            }
            case WEEKLY_COUNT -> {
                int target = h.getFrequencyTarget() == null || h.getFrequencyTarget() < 1 ? 1 : h.getFrequencyTarget();
                LocalDate w = FrequencyUtil.weekStart(start);
                LocalDate thisWeek = FrequencyUtil.weekStart(end);
                int count = 0;
                for (; w.isBefore(thisWeek); w = w.plusWeeks(1)) {
                    count += target;
                }
                // 当前周按已过天数分摊（不超过 target）
                int elapsed = end.getDayOfWeek().getValue(); // 周一=1
                count += Math.min(target, elapsed);
                yield count;
            }
        };
    }
}
