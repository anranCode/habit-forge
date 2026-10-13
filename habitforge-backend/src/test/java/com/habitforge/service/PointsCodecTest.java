package com.habitforge.service;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.study.service.support.PointsCodec;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 考点编解码守卫：normalize 的 trim/去重/上限是手动录入与 AI 识别(P6)共用的唯一口径。
 */
class PointsCodecTest {

    @Test
    void normalizeTrimsDedupsKeepsOrder() {
        List<String> out = PointsCodec.normalize(Arrays.asList(" 中值定理 ", "", "极限计算", "中值定理", "  "));
        assertEquals(Arrays.asList("中值定理", "极限计算"), out);
    }

    @Test
    void normalizeNullStaysNull() {
        // null=不改/未传 与 空数组=清空 的区分全靠这里原样透传 null
        assertNull(PointsCodec.normalize(null));
        assertTrue(PointsCodec.normalize(Arrays.asList(" ", "")).isEmpty());
    }

    @Test
    void normalizeRejectsTooLongOrTooMany() {
        assertThrows(BusinessException.class, () -> PointsCodec.normalize(List.of("一二三四五六七八九十".repeat(3))));
        List<String> nine = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(i -> "考点" + i).toList();
        assertThrows(BusinessException.class, () -> PointsCodec.normalize(nine));
    }

    @Test
    void storedRoundTripAndClearSemantics() {
        assertNull(PointsCodec.toStored(List.of()));       // 空集合 → NULL(清空)
        assertNull(PointsCodec.toStored(null));            // 未传 → NULL
        String json = PointsCodec.toStored(List.of("中值定理", "极限计算"));
        assertEquals(Arrays.asList("中值定理", "极限计算"), PointsCodec.parse(json));
        assertNull(PointsCodec.parse(""));
        assertNull(PointsCodec.parse("{坏掉的存量}"));        // 畸形按未标注处理, 不抛
    }

    @Test
    void likeElementQuotesWholeToken() {
        // 「函数」的 LIKE 模式必须带引号, 才不会命中「复合函数」
        assertEquals("\"函数\"", PointsCodec.likeElement(" 函数 "));
    }
}
