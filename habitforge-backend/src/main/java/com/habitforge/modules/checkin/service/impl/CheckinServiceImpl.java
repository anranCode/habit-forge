package com.habitforge.modules.checkin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.achievement.entity.Achievement;
import com.habitforge.modules.achievement.mapper.AchievementMapper;
import com.habitforge.modules.checkin.dto.CheckinRequest;
import com.habitforge.modules.checkin.dto.CheckinResponse;
import com.habitforge.modules.checkin.entity.Checkin;
import com.habitforge.modules.checkin.mapper.CheckinMapper;
import com.habitforge.modules.checkin.service.CheckinService;
import com.habitforge.modules.habit.entity.Habit;
import com.habitforge.modules.habit.mapper.HabitMapper;
import com.habitforge.modules.streak.entity.Streak;
import com.habitforge.modules.streak.service.StreakService;
import com.habitforge.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final CheckinMapper checkinMapper;
    private final HabitMapper habitMapper;
    private final AchievementMapper achievementMapper;
    private final StreakService streakService;
    private final UserService userService;
    private final RedisUtil redisUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinResponse checkin(String userId, CheckinRequest request) {
        // 简单限流：Redis 异常时放行（fail-open）
        try {
            if (!redisUtil.tryAcquire("checkin:" + userId, AppConstant.CHECKIN_RATE_LIMIT, Duration.ofSeconds(1))) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("限流组件不可用，放行: {}", e.getMessage());
        }

        // 1. 校验习惯归属
        Habit habit = habitMapper.selectById(request.getHabitId());
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HABIT_NOT_FOUND);
        }
        if (habit.getIsActive() != null && habit.getIsActive() == 0) {
            throw new BusinessException(ErrorCode.HABIT_ARCHIVED);
        }

        // 2. 校验日期
        LocalDate checkDate = request.getCheckDate() != null ? request.getCheckDate() : LocalDate.now();
        if (checkDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.CHECKIN_DATE_INVALID);
        }

        // 3. 写打卡记录（唯一键防重）
        Checkin checkin = new Checkin();
        checkin.setHabitId(habit.getId());
        checkin.setCheckDate(checkDate);
        checkin.setIsCompleted(1);
        checkin.setNote(request.getNote());
        try {
            checkinMapper.insert(checkin);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CHECKIN_DUPLICATE);
        }

        // 4. 全量重算习惯链
        Streak streak = streakService.recalculate(habit.getId());

        // 5. 积分与里程碑成就
        List<String> newAchievements = grantMilestones(userId, streak.getCurrentStreak());
        int total = AppConstant.POINTS_PER_CHECKIN + bonusOf(newAchievements);
        userService.addPoints(userId, total);

        log.info("打卡成功 user={}, habit={}, date={}, streak={}",
                userId, habit.getName(), checkDate, streak.getCurrentStreak());

        return CheckinResponse.builder()
                .checkin(CheckinResponse.CheckinInfo.builder()
                        .id(checkin.getId())
                        .habitId(checkin.getHabitId())
                        .checkDate(checkin.getCheckDate())
                        .isCompleted(true)
                        .note(checkin.getNote())
                        .build())
                .streak(CheckinResponse.StreakInfo.builder()
                        .currentStreak(streak.getCurrentStreak())
                        .longestStreak(streak.getLongestStreak())
                        .build())
                .pointsEarned(total)
                .newAchievements(newAchievements)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCheckin(String userId, String checkinId) {
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BusinessException(ErrorCode.CHECKIN_NOT_FOUND);
        }
        Habit habit = habitMapper.selectById(checkin.getHabitId());
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CHECKIN_NOT_FOUND);
        }
        checkinMapper.deleteById(checkinId);
        streakService.recalculate(checkin.getHabitId());
        // 扣回基础积分（里程碑奖励不追回），扣后不为负
        userService.addPoints(userId, -AppConstant.POINTS_PER_CHECKIN);
        log.info("撤销打卡 user={}, habit={}, date={}", userId, habit.getName(), checkin.getCheckDate());
    }

    @Override
    public List<Checkin> listByHabit(String userId, String habitId) {
        Habit habit = habitMapper.selectById(habitId);
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HABIT_NOT_FOUND);
        }
        return checkinMapper.selectList(new LambdaQueryWrapper<Checkin>()
                .eq(Checkin::getHabitId, habitId)
                .orderByDesc(Checkin::getCheckDate));
    }

    @Override
    public List<LocalDate> monthDates(String userId, YearMonth month) {
        // 用户的全部习惯
        List<Habit> habits = habitMapper.selectList(new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId)
                .select(Habit::getId));
        if (habits.isEmpty()) {
            return List.of();
        }
        List<String> habitIds = habits.stream().map(Habit::getId).toList();
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<Checkin> list = checkinMapper.selectList(new LambdaQueryWrapper<Checkin>()
                .in(Checkin::getHabitId, habitIds)
                .between(Checkin::getCheckDate, start, end)
                .select(Checkin::getCheckDate));
        return list.stream().map(Checkin::getCheckDate).distinct().sorted().toList();
    }

    @Override
    public boolean isCheckedOn(String habitId, LocalDate date) {
        return checkinMapper.selectCount(new LambdaQueryWrapper<Checkin>()
                .eq(Checkin::getHabitId, habitId)
                .eq(Checkin::getCheckDate, date)) > 0;
    }

    @Override
    public List<String> listCheckedHabitIds(String userId, LocalDate date) {
        // 两步查询：先查用户全部习惯（含已归档，历史打卡仍可关联），再查当日打卡
        List<Habit> habits = habitMapper.selectList(new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId)
                .select(Habit::getId));
        if (habits.isEmpty()) {
            return List.of();
        }
        List<String> habitIds = habits.stream().map(Habit::getId).toList();
        return checkinMapper.selectList(new LambdaQueryWrapper<Checkin>()
                        .in(Checkin::getHabitId, habitIds)
                        .eq(Checkin::getCheckDate, date)
                        .select(Checkin::getHabitId))
                .stream().map(Checkin::getHabitId).distinct().toList();
    }

    @Override
    public Checkin getByHabitAndDate(String habitId, LocalDate date) {
        return checkinMapper.selectOne(new LambdaQueryWrapper<Checkin>()
                .eq(Checkin::getHabitId, habitId)
                .eq(Checkin::getCheckDate, date)
                .last("LIMIT 1"));
    }

    // ================= 里程碑成就 =================

    /**
     * 检查并解锁里程碑成就，返回新解锁的成就名称
     */
    private List<String> grantMilestones(String userId, int currentStreak) {
        List<String> unlocked = new ArrayList<>();
        Set<String> owned = achievementMapper.selectList(new LambdaQueryWrapper<Achievement>()
                        .eq(Achievement::getUserId, userId)
                        .select(Achievement::getName))
                .stream().map(Achievement::getName).collect(Collectors.toSet());

        for (int i = 0; i < AppConstant.MILESTONE_DAYS.length; i++) {
            int days = AppConstant.MILESTONE_DAYS[i];
            String name = String.format(AppConstant.MILESTONE_ACHIEVEMENT_NAME, days);
            if (currentStreak >= days && !owned.contains(name)) {
                Achievement a = new Achievement();
                a.setUserId(userId);
                a.setName(name);
                a.setIcon("🔥");
                a.setDescription("任意习惯连续打卡达到 " + days + " 天");
                a.setAchievedAt(LocalDateTime.now());
                try {
                    achievementMapper.insert(a);
                    unlocked.add(name);
                } catch (DuplicateKeyException ignored) {
                    // 并发下已插入
                }
            }
        }
        return unlocked;
    }

    /** 根据本次新解锁的成就名称，累加对应的里程碑奖励积分 */
    private int bonusOf(List<String> unlockedNames) {
        int bonus = 0;
        for (String name : unlockedNames) {
            for (int i = 0; i < AppConstant.MILESTONE_DAYS.length; i++) {
                String expected = String.format(AppConstant.MILESTONE_ACHIEVEMENT_NAME, AppConstant.MILESTONE_DAYS[i]);
                if (expected.equals(name)) {
                    bonus += AppConstant.MILESTONE_POINTS[i];
                    break;
                }
            }
        }
        return bonus;
    }
}
