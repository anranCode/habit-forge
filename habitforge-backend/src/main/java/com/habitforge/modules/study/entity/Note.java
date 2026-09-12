package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习笔记（Markdown 原文入库, 前端渲染, 不落 HTML）
 */
@Data
@TableName("notes")
public class Note {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String subjectId;

    /** 所属章节(null=不挂章节) */
    private String chapterId;

    private String title;

    /** 正文(Markdown 原文) */
    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
