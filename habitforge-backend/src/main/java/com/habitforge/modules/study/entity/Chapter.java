package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 科目章节（树形: parent_id 自引用, 建议≤3层; 删除科目/父章节时 DB CASCADE 删子树）
 */
@Data
@TableName("chapters")
public class Chapter {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String subjectId;

    /** 父章节ID(null=顶层) */
    private String parentId;

    private String name;

    /** 排序(越小越靠前) */
    private Integer sortOrder;

    /** NOT_STARTED / IN_PROGRESS / DONE */
    private String status;

    /** 首次完成时间(改回非 DONE 时置 null) */
    private LocalDateTime doneAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
