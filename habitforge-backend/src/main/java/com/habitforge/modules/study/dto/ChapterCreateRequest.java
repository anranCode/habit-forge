package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建章节请求
 */
@Data
public class ChapterCreateRequest {

    @NotBlank(message = "科目ID不能为空")
    private String subjectId;

    /** 父章节ID, null=顶层 */
    private String parentId;

    @NotBlank(message = "章节名称不能为空")
    @Size(max = 200, message = "章节名称最长 200 字")
    private String name;

    /** 排序(越小越靠前), 默认 0 */
    private Integer sortOrder;
}
