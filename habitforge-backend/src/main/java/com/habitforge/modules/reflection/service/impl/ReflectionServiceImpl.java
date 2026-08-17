package com.habitforge.modules.reflection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.checkin.entity.Checkin;
import com.habitforge.modules.checkin.service.CheckinService;
import com.habitforge.modules.habit.entity.Habit;
import com.habitforge.modules.habit.mapper.HabitMapper;
import com.habitforge.modules.journal.entity.Journal;
import com.habitforge.modules.journal.entity.JournalHabit;
import com.habitforge.modules.journal.mapper.JournalHabitMapper;
import com.habitforge.modules.journal.mapper.JournalMapper;
import com.habitforge.modules.journal.service.JournalService;
import com.habitforge.modules.reflection.dto.ReflectionRequest;
import com.habitforge.modules.reflection.dto.ReflectionResponse;
import com.habitforge.modules.reflection.dto.ReflectionUpdateRequest;
import com.habitforge.modules.reflection.entity.HabitReflection;
import com.habitforge.modules.reflection.mapper.HabitReflectionMapper;
import com.habitforge.modules.reflection.service.ReflectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionServiceImpl implements ReflectionService {

    private final HabitReflectionMapper reflectionMapper;
    private final JournalMapper journalMapper;
    private final JournalHabitMapper journalHabitMapper;
    private final HabitMapper habitMapper;
    private final JournalService journalService;
    private final CheckinService checkinService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReflectionResponse create(String userId, ReflectionRequest request) {
        // 1. 校验日记归属
        Journal journal = journalService.getOwned(userId, request.getJournalId());

        // 2. 校验习惯归属
        Habit habit = habitMapper.selectById(request.getHabitId());
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HABIT_NOT_FOUND);
        }

        // 3. 自动建立日记-习惯关联（幂等）
        linkJournalHabit(journal.getId(), habit.getId());

        // 4. 自动回填当日打卡记录（未完成/未打卡时为 null）
        Checkin checkin = checkinService.getByHabitAndDate(habit.getId(), journal.getJournalDate());

        HabitReflection reflection = new HabitReflection();
        reflection.setJournalId(journal.getId());
        reflection.setHabitId(habit.getId());
        reflection.setCheckinId(checkin != null ? checkin.getId() : null);
        reflection.setResult(request.getResult());
        reflection.setFeeling(request.getFeeling());
        reflection.setDifficulty(request.getDifficulty());
        reflection.setReason(request.getReason());
        reflection.setObstacle(request.getObstacle());
        reflection.setLearning(request.getLearning());
        reflection.setAdjustment(request.getAdjustment());
        try {
            reflectionMapper.insert(reflection);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.REFLECTION_DUPLICATE);
        }

        log.info("心得记录成功 user={}, habit={}, journal={}", userId, habit.getName(), journal.getJournalDate());
        return toResponse(reflection, habit.getName(), journal);
    }

    @Override
    public ReflectionResponse update(String userId, String id, ReflectionUpdateRequest request) {
        HabitReflection reflection = getOwned(userId, id);
        if (request.getResult() != null) {
            reflection.setResult(request.getResult());
        }
        if (request.getFeeling() != null) {
            reflection.setFeeling(request.getFeeling());
        }
        if (request.getDifficulty() != null) {
            reflection.setDifficulty(request.getDifficulty());
        }
        if (request.getReason() != null) {
            reflection.setReason(request.getReason());
        }
        if (request.getObstacle() != null) {
            reflection.setObstacle(request.getObstacle());
        }
        if (request.getLearning() != null) {
            reflection.setLearning(request.getLearning());
        }
        if (request.getAdjustment() != null) {
            reflection.setAdjustment(request.getAdjustment());
        }
        reflectionMapper.updateById(reflection);

        Journal journal = journalMapper.selectById(reflection.getJournalId());
        Habit habit = habitMapper.selectById(reflection.getHabitId());
        return toResponse(reflection, habit != null ? habit.getName() : null, journal);
    }

    @Override
    public void delete(String userId, String id) {
        HabitReflection reflection = getOwned(userId, id);
        reflectionMapper.deleteById(reflection.getId());
    }

    @Override
    public List<ReflectionResponse> listByJournal(String userId, String journalId) {
        Journal journal = journalService.getOwned(userId, journalId);
        List<HabitReflection> list = reflectionMapper.selectList(new LambdaQueryWrapper<HabitReflection>()
                .eq(HabitReflection::getJournalId, journalId)
                .orderByAsc(HabitReflection::getCreatedAt));
        if (list.isEmpty()) {
            return List.of();
        }
        List<String> habitIds = list.stream().map(HabitReflection::getHabitId).distinct().toList();
        Map<String, Habit> habitMap = habitMapper.selectBatchIds(habitIds).stream()
                .collect(Collectors.toMap(Habit::getId, Function.identity()));
        return list.stream()
                .map(r -> toResponse(r, habitMap.containsKey(r.getHabitId()) ? habitMap.get(r.getHabitId()).getName() : null, journal))
                .toList();
    }

    @Override
    public List<ReflectionResponse> listByHabit(String userId, String habitId) {
        Habit habit = habitMapper.selectById(habitId);
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HABIT_NOT_FOUND);
        }
        List<HabitReflection> list = reflectionMapper.selectList(new LambdaQueryWrapper<HabitReflection>()
                .eq(HabitReflection::getHabitId, habitId)
                .orderByDesc(HabitReflection::getCreatedAt));
        if (list.isEmpty()) {
            return List.of();
        }
        List<String> journalIds = list.stream().map(HabitReflection::getJournalId).distinct().toList();
        Map<String, Journal> journalMap = journalMapper.selectBatchIds(journalIds).stream()
                .collect(Collectors.toMap(Journal::getId, Function.identity()));
        return list.stream()
                .map(r -> toResponse(r, habit.getName(), journalMap.get(r.getJournalId())))
                .toList();
    }

    // ================= 内部方法 =================

    /** 归属校验：心得存在且所属日记属于当前用户 */
    private HabitReflection getOwned(String userId, String reflectionId) {
        HabitReflection reflection = reflectionMapper.selectById(reflectionId);
        if (reflection == null) {
            throw new BusinessException(ErrorCode.REFLECTION_NOT_FOUND);
        }
        journalService.getOwned(userId, reflection.getJournalId());
        return reflection;
    }

    /** 日记-习惯关联（唯一键防重复） */
    private void linkJournalHabit(String journalId, String habitId) {
        Long count = journalHabitMapper.selectCount(new LambdaQueryWrapper<JournalHabit>()
                .eq(JournalHabit::getJournalId, journalId)
                .eq(JournalHabit::getHabitId, habitId));
        if (count != null && count > 0) {
            return;
        }
        JournalHabit link = new JournalHabit();
        link.setJournalId(journalId);
        link.setHabitId(habitId);
        try {
            journalHabitMapper.insert(link);
        } catch (DuplicateKeyException ignored) {
            // 并发下已插入
        }
    }

    private ReflectionResponse toResponse(HabitReflection r, String habitName, Journal journal) {
        return ReflectionResponse.builder()
                .id(r.getId())
                .journalId(r.getJournalId())
                .habitId(r.getHabitId())
                .checkinId(r.getCheckinId())
                .result(r.getResult())
                .feeling(r.getFeeling())
                .difficulty(r.getDifficulty())
                .reason(r.getReason())
                .obstacle(r.getObstacle())
                .learning(r.getLearning())
                .adjustment(r.getAdjustment())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .habitName(habitName)
                .journalDate(journal != null ? journal.getJournalDate() : null)
                .journalTitle(journal != null ? journal.getTitle() : null)
                .build();
    }
}
