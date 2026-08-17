package com.habitforge.modules.journal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日记-习惯关联（多对多，唯一键防重复）
 */
@Data
@TableName("journal_habits")
public class JournalHabit {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String journalId;

    private String habitId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
