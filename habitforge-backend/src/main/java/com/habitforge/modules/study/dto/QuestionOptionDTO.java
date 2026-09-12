package com.habitforge.modules.study.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题目选项（options 列以 JSON 数组字符串存储, DTO 出入为本类型列表）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOptionDTO {

    /** 选项键, 如 A/B/C/D */
    private String key;

    /** 选项文本 */
    private String text;
}
