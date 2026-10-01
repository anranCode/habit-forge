package com.habitforge.modules.review.dto;

import com.habitforge.modules.review.entity.Review;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 周报响应
 */
@Data
@Builder
public class WeeklyReportResponse {

    private String id;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate reviewDate;
    private String title;
    /** 综合评分 0-100 */
    private Integer score;
    private String goodThings;
    private String badThings;
    private String learnings;
    /** 改进建议（多条以换行分隔） */
    private String suggestions;
    private Boolean aiGenerated;
    private String model;
    private Integer totalTokens;
    /** 客观数据快照 JSON（前端「本期数据」折叠区直接渲染原始数据） */
    private String statsSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WeeklyReportResponse from(Review r) {
        return WeeklyReportResponse.builder()
                .id(r.getId())
                .periodStart(r.getPeriodStart())
                .periodEnd(r.getPeriodEnd())
                .reviewDate(r.getReviewDate())
                .title(r.getTitle())
                .score(r.getScore())
                .goodThings(r.getGoodThings())
                .badThings(r.getBadThings())
                .learnings(r.getLearnings())
                .suggestions(r.getSuggestions())
                .aiGenerated(r.getAiGenerated())
                .model(r.getModel())
                .totalTokens(r.getTotalTokens())
                .statsSnapshot(r.getStatsSnapshot())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
