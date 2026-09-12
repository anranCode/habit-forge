package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建笔记请求（content 为 Markdown 原文, 可空）
 */
@Data
public class NoteCreateRequest {

    @NotBlank(message = "科目ID不能为空")
    private String subjectId;

    /** 章节ID, null=不挂章节 */
    private String chapterId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    private String content;
}
