package com.habitforge.modules.habit.service;

import com.habitforge.modules.habit.dto.*;

import java.util.List;

public interface HabitService {

    HabitResponseDTO create(String userId, HabitCreateDTO dto);

    HabitResponseDTO update(String userId, String habitId, HabitUpdateDTO dto);

    void delete(String userId, String habitId);

    HabitResponseDTO getDetail(String userId, String habitId);

    /** 我的习惯列表（含链与今日状态） */
    List<HabitResponseDTO> listMine(String userId, Boolean activeOnly);

    /** 今日待打卡（按频率过滤，含"绝不错过两次"提醒标记） */
    List<HabitResponseDTO> listToday(String userId);

    /** 追踪看板统计 */
    HabitStatsDTO getStats(String userId);
}
