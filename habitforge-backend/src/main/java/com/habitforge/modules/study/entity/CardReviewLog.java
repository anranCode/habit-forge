package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卡片复习日志（每次评分一行, 前后快照; 唯一快增长表, 留年度归档运维项）
 */
@Data
@TableName("card_review_logs")
public class CardReviewLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String cardId;

    /** 冗余所属用户(按人按时间查询) */
    private String userId;

    /** 1忘记 / 2模糊 / 3记得 / 4轻松 */
    private Integer rating;

    private Integer intervalBefore;

    private Integer intervalAfter;

    private BigDecimal easeBefore;

    private BigDecimal easeAfter;

    /** 复习时间(不设置时走 DB 默认 CURRENT_TIMESTAMP) */
    private LocalDateTime reviewedAt;
}
