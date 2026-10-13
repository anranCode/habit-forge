package com.habitforge.service;

import com.habitforge.modules.study.dto.PracticeQuestionResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 防透题的形状守卫（计划验收项）：random 下发 DTO 永远不得长出 answer/analysis 字段。
 * 加字段本该同时改查询列裁剪与前端契约, 这条测试把"忘了改其中一处"变成红灯。
 */
class PracticeShapeGuardTest {

    @Test
    void practiceResponseHasNoAnswerFields() {
        Set<String> fields = Arrays.stream(PracticeQuestionResponse.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(fields.contains("answer"), "PracticeQuestionResponse 出现 answer 字段 —— 透题");
        assertFalse(fields.contains("analysis"), "PracticeQuestionResponse 出现 analysis 字段 —— 透题");
        assertEquals(Set.of("id", "questionType", "stem", "options", "difficulty"), fields);
    }
}
