package com.habitforge.modules.focus.service;

import com.habitforge.modules.focus.dto.FocusDayPoint;
import com.habitforge.modules.focus.dto.FocusLogRequest;
import com.habitforge.modules.focus.dto.FocusLogResponse;
import com.habitforge.modules.focus.dto.FocusTrendResponse;
import com.habitforge.modules.focus.dto.UrgeRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * 注意力/节制（P2 手机节制）: 娱乐时长录入 + 冲动抵抗记录 + 节制达标积分
 */
public interface FocusService {

    /** 今日注意力状态（未录入也返回空壳，前端不必判 null） */
    FocusLogResponse today(String userId);

    /** 录入/修改某日娱乐时长（差量结算节制达标积分） */
    FocusLogResponse saveLog(String userId, FocusLogRequest request);

    /** 记一次「想刷手机」的冲动（只能记今天；忍住与否都记） */
    FocusLogResponse recordUrge(String userId, UrgeRequest request);

    /** 区间趋势（含没录入的日子, minutes 为 null） */
    FocusTrendResponse trend(String userId, LocalDate from, LocalDate to);

    /** 设置每日娱乐时长上限（不影响当日已结算的积分） */
    FocusLogResponse updateLimit(String userId, Integer limitMinutes);

    /** 供周报组装使用: 区间每日数据（不做区间长度校验, 由调用方保证） */
    List<FocusDayPoint> rangeForReport(String userId, LocalDate from, LocalDate to);

    /** 当前生效的每日娱乐时长上限（分钟） */
    int resolveLimit(String userId);
}
