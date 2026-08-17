package com.habitforge.modules.journal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日记图片：MySQL 只存 objectKey+元数据，字节在 MinIO
 */
@Data
@TableName("journal_images")
public class JournalImage {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String journalId;

    /** MinIO 对象键: habitforge/journal/{yyyy}/{MM}/{dd}/{uuid}.{ext} */
    private String objectKey;

    private String originalName;

    private String contentType;

    private Integer fileSize;

    private Integer width;

    private Integer height;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
