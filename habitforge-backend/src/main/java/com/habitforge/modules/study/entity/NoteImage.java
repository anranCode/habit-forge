package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记图片：完全镜像 journal_images 约定, MySQL 只存 objectKey+元数据, 字节在 MinIO
 */
@Data
@TableName("note_images")
public class NoteImage {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String noteId;

    /** MinIO 对象键: habitforge/note/{yyyy}/{MM}/{uuid}.{ext} */
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
