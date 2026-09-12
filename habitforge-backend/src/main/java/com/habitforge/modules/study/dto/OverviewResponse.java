package com.habitforge.modules.study.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 学习总览（供 Home 卡片与 P2 AiScheduleService 共用; 卡/错题字段 P0 恒 0, P1 补齐）
 */
@Data
@Builder
public class OverviewResponse {

    private List<SubjectResponse> subjects;
    /** 全部科目到期闪卡数(P1 实现, P0 恒 0) */
    private Integer dueCardsTotal;
    /** 全部待复习错题数(P1 实现, P0 恒 0) */
    private Integer wrongsTotal;
    /** 今日已复习卡片数(P1 实现, P0 恒 0) */
    private Integer reviewedToday;
}
