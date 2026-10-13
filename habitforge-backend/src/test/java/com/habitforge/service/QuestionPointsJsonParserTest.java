package com.habitforge.service;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.ai.client.QuestionPointsJsonParser;
import com.habitforge.modules.ai.dto.QuestionPointsDraft;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 考点草稿清洗：trim/去空/去重保序/截字/截个数/有效项 <1 抛 6005。
 * 模型输出里有 null 条目，List.of 会先于被测代码炸掉 —— 用 Arrays.asList。
 */
class QuestionPointsJsonParserTest {

    @Test
    void parse_happyPath_keepsOrderAndContent() {
        List<String> points = QuestionPointsJsonParser.parse(
                new QuestionPointsDraft(List.of("中值定理", "复合函数求导")), 8, 20);
        assertEquals(List.of("中值定理", "复合函数求导"), points);
    }

    @Test
    void parse_trimsBlanksAndDedupesPreservingFirst() {
        List<String> points = QuestionPointsJsonParser.parse(
                new QuestionPointsDraft(Arrays.asList("  导数定义 ", null, "", "导数定义", "极限计算")), 8, 20);
        assertEquals(List.of("导数定义", "极限计算"), points);
    }

    @Test
    void parse_truncatesOverlongPointToMaxChars() {
        String longPoint = "相".repeat(30);
        List<String> points = QuestionPointsJsonParser.parse(
                new QuestionPointsDraft(List.of(longPoint)), 8, 20);
        assertEquals(1, points.size());
        assertEquals("相".repeat(20), points.get(0));
    }

    @Test
    void parse_capsCountAtMaxPoints() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add("考点" + i);
        }
        List<String> points = QuestionPointsJsonParser.parse(new QuestionPointsDraft(many), 8, 20);
        assertEquals(8, points.size());
        assertEquals("考点0", points.get(0));
        assertEquals("考点7", points.get(7));
    }

    @Test
    void parse_allBlank_throws6005() {
        assertThrows(BusinessException.class, () -> QuestionPointsJsonParser.parse(
                new QuestionPointsDraft(Arrays.asList(null, "  ", "")), 8, 20));
    }

    @Test
    void parse_nullDraft_throws6005() {
        assertThrows(BusinessException.class,
                () -> QuestionPointsJsonParser.parse(null, 8, 20));
    }
}
