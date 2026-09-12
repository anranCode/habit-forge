package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建闪卡请求（due_date 初始=创建日, EF=2.50, 队列新卡自然混入）
 */
@Data
public class FlashcardCreateRequest {

    @NotBlank(message = "科目ID不能为空")
    private String subjectId;

    /** 章节ID, null=不挂章节 */
    private String chapterId;

    @NotBlank(message = "卡面不能为空")
    @Size(max = 2000, message = "卡面最长 2000 字")
    private String front;

    @NotBlank(message = "卡背不能为空")
    @Size(max = 2000, message = "卡背最长 2000 字")
    private String back;
}
