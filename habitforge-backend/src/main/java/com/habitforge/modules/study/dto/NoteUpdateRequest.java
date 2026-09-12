package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新笔记请求（各字段可选, null=不改）
 */
@Data
public class NoteUpdateRequest {

    private String chapterId;

    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    private String content;
}
