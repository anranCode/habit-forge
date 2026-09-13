package com.habitforge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 计划时间块（habit/subject/chapter 引用列 FK SET NULL: 源删了不连删块;
 * 状态机: PROPOSED→adopt→ADOPTED→complete→DONE→reopen→ADOPTED; PROPOSED/ADOPTED→skip→SKIPPED）
 */
@Data
@TableName("plan_blocks")
public class PlanBlock {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属每日计划 */
    private String planId;

    /** 关联习惯（AI 只能从候选清单挑选, 防幻觉白名单校验） */
    private String habitId;

    /** 关联科目 */
    private String subjectId;

    /** 关联章节 */
    private String chapterId;

    /** HABIT / STUDY / REST / OTHER */
    private String blockType;

    /** 标题（中文≤30 字由 prompt+校验约束） */
    private String title;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 排序（越小越靠前, 按 start 时间） */
    private Integer sortOrder;

    /** PROPOSED / ADOPTED / DONE / SKIPPED */
    private String status;

    /** AI 生成 / MANUAL 手动（手动块创建即 ADOPTED） */
    private String source;

    /** 完成时间（仅 DONE 非空） */
    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
