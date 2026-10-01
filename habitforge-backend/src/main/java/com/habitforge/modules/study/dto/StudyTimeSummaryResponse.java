package com.habitforge.modules.study.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 某日学习时长汇总（Home / 学习中心卡片）
 */
@Data
@Builder
public class StudyTimeSummaryResponse {

    private LocalDate date;
    /** 当日总分钟（仅统计已结束的记录） */
    private Integer minutes;
    /** 当日记录段数 */
    private Integer sessionCount;
}
