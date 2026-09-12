package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 更新科目请求（字段均可选, null 表示不修改）
 */
@Data
public class SubjectUpdateRequest {

    @Size(max = 100, message = "科目名称最长 100 字")
    private String name;

    private LocalDate examDate;

    @Size(max = 50, message = "考试学期最长 50 字")
    private String examSession;

    @Size(max = 500, message = "科目说明最长 500 字")
    private String description;

    private Integer sortOrder;
}
