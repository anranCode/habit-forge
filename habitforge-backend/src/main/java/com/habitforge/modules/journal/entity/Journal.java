package com.habitforge.modules.journal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日记（每日记录）：user × day 一天一篇
 */
@Data
@TableName("journals")
public class Journal {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** 日记日期（可补写过去，区别于 createdAt） */
    private LocalDate journalDate;

    private String title;

    /** 心情: 1好 2一般 3疲惫 */
    private Integer mood;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
