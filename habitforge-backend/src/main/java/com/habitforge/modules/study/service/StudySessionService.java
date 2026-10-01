package com.habitforge.modules.study.service;

import com.habitforge.modules.study.dto.DailyStudyTimeResponse;
import com.habitforge.modules.study.dto.StudySessionManualRequest;
import com.habitforge.modules.study.dto.StudySessionResponse;
import com.habitforge.modules.study.dto.StudySessionStartRequest;
import com.habitforge.modules.study.dto.StudyTimeSummaryResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习时长（P0）: 计时器 + 手动补录 + 按日/区间聚合
 */
public interface StudySessionService {

    /** 开始计时（已有进行中的计时返 7010） */
    StudySessionResponse start(String userId, StudySessionStartRequest request);

    /** 结束计时并回填分钟（已结束返 7011; 超过 12 小时返 7012 提示改为补录） */
    StudySessionResponse end(String userId, String sessionId);

    /** 手动补录（不允许未来日期） */
    StudySessionResponse manual(String userId, StudySessionManualRequest request);

    /** 当前进行中的记录, 无则 null（前端刷新后恢复计时） */
    StudySessionResponse active(String userId);

    /** 某日全部记录（含进行中, 按开始时间升序） */
    List<StudySessionResponse> listByDate(String userId, LocalDate date);

    /** 删除记录（含误开的计时） */
    void delete(String userId, String sessionId);

    /** 某日汇总（只统计已结束的记录） */
    StudyTimeSummaryResponse summary(String userId, LocalDate date);

    /** 区间内每日汇总（含 0 值补洞, 区间上限 366 天） */
    List<DailyStudyTimeResponse> daily(String userId, LocalDate from, LocalDate to);

    /** 供周报组装: 区间内已结束记录的原始分钟数（按日） */
    List<DailyStudyTimeResponse> dailyForReport(String userId, LocalDate from, LocalDate to);

    /** 区间内已结束记录明细（周报按科目汇总用; 按开始时间升序） */
    List<StudySessionResponse> listByRange(String userId, LocalDate from, LocalDate to);
}
