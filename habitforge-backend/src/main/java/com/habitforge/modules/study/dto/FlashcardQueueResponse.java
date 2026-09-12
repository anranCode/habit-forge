package com.habitforge.modules.study.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 复习队列响应（due&lt;=today 的 ACTIVE 卡按 due_date,created_at 排序取 limit 张）
 */
@Data
@Builder
public class FlashcardQueueResponse {

    private List<FlashcardResponse> cards;

    /** 今日到期总数(due&lt;=today AND ACTIVE) */
    private Integer dueTotal;

    /** 今日到期的新卡总数(repetition=0 AND due&lt;=today, dueTotal 的子集) */
    private Integer newTotal;
}
