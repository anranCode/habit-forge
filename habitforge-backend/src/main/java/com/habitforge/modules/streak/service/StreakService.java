package com.habitforge.modules.streak.service;

import com.habitforge.modules.streak.entity.Streak;

import java.util.List;
import java.util.Map;

public interface StreakService {

    /** 获取某习惯的链（不存在则返回空链对象） */
    Streak getByHabitId(String habitId);

    /** 全量重算某习惯的链（打卡/撤销打卡后调用） */
    Streak recalculate(String habitId);

    /** 习惯链排行（当前用户，按当前链长倒序） */
    List<Map<String, Object>> topStreaks(String userId, int limit);
}
