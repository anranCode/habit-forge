package com.habitforge.modules.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 复盘记录（表为复盘预留, P0 先承载 WEEKLY 周报）
 *
 * <p>good_things/bad_things/learnings/suggestions 四段由 AI 生成初稿, 用户可继续编辑;
 * stats_snapshot 存喂给 AI 的同源客观数据 JSON, 保证「报告可复现、可审计」。
 */
@Data
@TableName("reviews")
public class Review {

    public static final String TYPE_WEEKLY = "WEEKLY";

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** DAILY / WEEKLY / MONTHLY / QUARTERLY */
    private String type;

    private String title;

    /** 做得好的事 */
    private String goodThings;

    /** 做得不好的事 */
    private String badThings;

    /** 学到的东西 */
    private String learnings;

    /** 改进建议（AI 生成, 多条以换行分隔） */
    private String suggestions;

    /** 综合评分 0-100（AI 生成, 用户可改） */
    private Integer score;

    /** 统计区间起（WEEKLY = 周一） */
    private LocalDate periodStart;

    /** 统计区间止（WEEKLY = 周日） */
    private LocalDate periodEnd;

    /** 客观数据快照 JSON */
    private String statsSnapshot;

    /** 内容是否由 AI 生成 */
    private Boolean aiGenerated;

    private String model;

    private Integer totalTokens;

    /** 复盘日期（周报 = 生成当天） */
    private LocalDate reviewDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
