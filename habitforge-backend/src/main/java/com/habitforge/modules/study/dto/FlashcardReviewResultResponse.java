package com.habitforge.modules.study.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单次评分结果 + 当日达标奖励
 */
@Data
@Builder
public class FlashcardReviewResultResponse {

    private Integer intervalDays;
    private BigDecimal easeFactor;
    /** 下次到期日(rating=1 时为今天) */
    private LocalDate dueDate;
    /** 当日累计复习张数(含本次) */
    private Integer reviewedToday;
    /** 今日复习达标奖励积分, 未达标/已发过为 0 */
    private Integer rewardPoints;
}
