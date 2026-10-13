package com.habitforge.service;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.ai.client.ShortGradeJsonParser;
import com.habitforge.modules.ai.dto.ShortGradeDraft;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判分草稿清洗：坏条目丢弃(hit 缺失/point 空白)、同文去重、超长截断、超 8 条截断、
 * 全军覆没 → 6005; countCovered 只数 true。
 */
class ShortGradeJsonParserTest {

    private static ShortGradeDraft draft(ShortGradeDraft.PointDraft... pts) {
        // Arrays.asList 而非 List.of: 模型输出里有 null 条目, List.of 会先于被测代码炸掉
        return new ShortGradeDraft(Arrays.asList(pts));
    }

    @Test
    void dropsMalformedEntries_keepsGoodOnes() {
        ShortGradeDraft d = draft(
                new ShortGradeDraft.PointDraft("  要点一  ", true),
                new ShortGradeDraft.PointDraft("要点二", null),   // hit 缺失: 丢弃(不蒙判)
                new ShortGradeDraft.PointDraft("   ", true),      // point 空白: 丢弃
                null,
                new ShortGradeDraft.PointDraft("要点一", false)); // 截断后同文: 去重保先

        List<ShortGradeDraft.PointDraft> out = ShortGradeJsonParser.parse(d);

        assertEquals(1, out.size());
        assertEquals("要点一", out.get(0).point());
        assertTrue(out.get(0).hit());
        assertEquals(1, ShortGradeJsonParser.countCovered(out));
    }

    @Test
    void truncatesLongPointsTo50Chars() {
        String longPoint = "点".repeat(60);
        List<ShortGradeDraft.PointDraft> out =
                ShortGradeJsonParser.parse(draft(new ShortGradeDraft.PointDraft(longPoint, true)));
        assertEquals(50, out.get(0).point().length());
    }

    @Test
    void capsAtEightPoints() {
        List<ShortGradeDraft.PointDraft> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(new ShortGradeDraft.PointDraft("要点" + i, i % 2 == 0));
        }
        List<ShortGradeDraft.PointDraft> out = ShortGradeJsonParser.parse(new ShortGradeDraft(many));
        assertEquals(8, out.size());
        assertEquals(4, ShortGradeJsonParser.countCovered(out));
    }

    @Test
    void allEntriesInvalidOrEmpty_throws6005() {
        assertThrows(BusinessException.class, () -> ShortGradeJsonParser.parse(null));
        assertThrows(BusinessException.class, () -> ShortGradeJsonParser.parse(new ShortGradeDraft(null)));
        assertThrows(BusinessException.class, () -> ShortGradeJsonParser.parse(draft()));
        assertThrows(BusinessException.class,
                () -> ShortGradeJsonParser.parse(draft(new ShortGradeDraft.PointDraft("无 hit", null))));
    }
}
