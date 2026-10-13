package com.habitforge.modules.study.dto;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.habitforge.modules.study.entity.Question;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 刷题下发题目（防透题契约：本类**没有** answer/analysis 字段 ——
 * 查询侧 .select() 列裁剪让答案根本不出库，类型侧再由 PracticeShapeGuardTest 反射断言兜底，
 * 加字段要闯两道门）。
 */
@Data
@Builder
public class PracticeQuestionResponse {

    private String id;
    private String questionType;
    private String stem;
    /** SINGLE/MULTI 为选项列表; JUDGE 为 null */
    private List<QuestionOptionDTO> options;
    private Integer difficulty;

    /** 只读五列的工厂：即使未来实体加字段也不会顺带漏出去 */
    public static PracticeQuestionResponse from(Question question) {
        return PracticeQuestionResponse.builder()
                .id(question.getId())
                .questionType(question.getQuestionType())
                .stem(question.getStem())
                .options(StrUtil.isBlank(question.getOptions())
                        ? null
                        : JSONUtil.toList(question.getOptions(), QuestionOptionDTO.class))
                .difficulty(question.getDifficulty())
                .build();
    }
}
