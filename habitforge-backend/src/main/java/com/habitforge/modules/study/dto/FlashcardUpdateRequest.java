package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新闪卡请求（各字段可选, null=不改; 只改内容不动调度字段）
 */
@Data
public class FlashcardUpdateRequest {

    private String subjectId;

    private String chapterId;

    @Size(max = 2000, message = "卡面最长 2000 字")
    private String front;

    @Size(max = 2000, message = "卡背最长 2000 字")
    private String back;
}
