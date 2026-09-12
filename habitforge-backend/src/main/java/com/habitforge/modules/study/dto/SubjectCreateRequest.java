package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建科目请求
 */
@Data
public class SubjectCreateRequest {

    @NotBlank(message = "科目名称不能为空")
    @Size(max = 100, message = "科目名称最长 100 字")
    private String name;

    /** 考试日期(倒计时用), 可不填 */
    private LocalDate examDate;

    @Size(max = 50, message = "考试学期最长 50 字")
    private String examSession;

    @Size(max = 500, message = "科目说明最长 500 字")
    private String description;

    /** 排序(越小越靠前), 默认 0 */
    private Integer sortOrder;
}
