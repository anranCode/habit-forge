package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新章节请求（字段均可选, null 表示不修改; parentId 换父时沿链上溯防环）
 */
@Data
public class ChapterUpdateRequest {

    @Size(max = 200, message = "章节名称最长 200 字")
    private String name;

    private String parentId;

    private Integer sortOrder;
}
