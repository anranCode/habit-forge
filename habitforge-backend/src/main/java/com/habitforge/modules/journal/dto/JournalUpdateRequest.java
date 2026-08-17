package com.habitforge.modules.journal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新日记请求（字段均可选，null 表示不修改）
 */
@Data
public class JournalUpdateRequest {

    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    @Min(value = 1, message = "心情取值 1-3")
    @Max(value = 3, message = "心情取值 1-3")
    private Integer mood;

    @Size(max = 10000, message = "正文最长 10000 字")
    private String content;
}
