package com.habitforge.modules.checkin.service;

import com.habitforge.modules.checkin.dto.CheckinRequest;
import com.habitforge.modules.checkin.dto.CheckinResponse;
import com.habitforge.modules.checkin.entity.Checkin;

import java.time.YearMonth;
import java.util.List;

public interface CheckinService {

    /** 打卡（含频率校验、重算链、加积分、成就） */
    CheckinResponse checkin(String userId, CheckinRequest request);

    /** 撤销打卡 */
    void cancelCheckin(String userId, String checkinId);

    /** 某习惯的全部打卡记录（倒序） */
    List<Checkin> listByHabit(String userId, String habitId);

    /** 某月所有打卡日期（供热力图，去重） */
    List<java.time.LocalDate> monthDates(String userId, YearMonth month);

    /** 判断某习惯在某天是否已打卡 */
    boolean isCheckedOn(String habitId, java.time.LocalDate date);

    /** 某用户某天已打卡的习惯ID列表（供日记自动关联） */
    List<String> listCheckedHabitIds(String userId, java.time.LocalDate date);

    /** 某习惯某天的打卡记录，不存在返回 null（供心得关联打卡） */
    Checkin getByHabitAndDate(String habitId, java.time.LocalDate date);
}
