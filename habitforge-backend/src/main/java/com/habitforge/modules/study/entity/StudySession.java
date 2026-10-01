package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习计时（一段专注一行; 进行中 = endedAt 为 null）
 *
 * <p>MANUAL 补录行的 startedAt 只是占位（取 sessionDate 当天 00:00），
 * 真实语义只有 minutes 与 sessionDate，前端对 MANUAL 不展示时间区间。
 */
@Data
@TableName("study_sessions")
public class StudySession {

    /** 计时器产生 */
    public static final String SOURCE_TIMER = "TIMER";
    /** 手动补录 */
    public static final String SOURCE_MANUAL = "MANUAL";

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** 关联科目（可选） */
    private String subjectId;

    /** 关联章节（可选） */
    private String chapterId;

    /** 归属日期（取 startedAt 的自然日, 冗余便于按日聚合） */
    private LocalDate sessionDate;

    private LocalDateTime startedAt;

    /** null = 进行中 */
    private LocalDateTime endedAt;

    /** 分钟数（结束时由服务端计算） */
    private Integer minutes;

    /** TIMER / MANUAL */
    private String source;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 是否仍在计时中 */
    public boolean isRunning() {
        return endedAt == null;
    }
}
