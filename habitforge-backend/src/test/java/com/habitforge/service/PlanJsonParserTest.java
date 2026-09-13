package com.habitforge.service;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.ai.client.PlanJsonParser;
import com.habitforge.modules.ai.dto.PlanDraft;
import com.habitforge.modules.ai.dto.PlanDraft.PlanDraftBlock;
import com.habitforge.modules.ai.entity.PlanBlock;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 草稿业务校验层：时间/白名单 ID/类型/标题/去重/上下限
 */
class PlanJsonParserTest {

    private static final Set<String> HABITS = Set.of("h1", "h2");
    private static final Set<String> SUBJECTS = Set.of("s1");
    private static final Set<String> CHAPTERS = Set.of("c1");
    private static final int MIN = 3;
    private static final int MAX = 12;

    private static PlanDraftBlock b(String start, String end, String title, String type,
                                    String habitId, String subjectId, String chapterId) {
        return new PlanDraftBlock(start, end, title, type, habitId, subjectId, chapterId);
    }

    private static PlanDraft draft(PlanDraftBlock... blocks) {
        return new PlanDraft(List.of(blocks));
    }

    private static List<PlanBlock> parse(PlanDraft d) {
        return PlanJsonParser.parse(d, HABITS, SUBJECTS, CHAPTERS, MIN, MAX);
    }

    private static int codeOf(Runnable r) {
        BusinessException e = assertThrows(BusinessException.class, r::run);
        return e.getCode();
    }

    @Test
    void validDraft_mapsFieldsAndSortOrder() {
        List<PlanBlock> blocks = parse(draft(
                b("09:00", "10:00", "晨跑", "HABIT", "h1", null, null),
                b("07:00", "08:00", "背单词", "STUDY", null, "s1", "c1"),
                b("12:00", "12:30", "午休", "REST", null, null, null)));
        assertEquals(3, blocks.size());
        // 按 start 排序 + sortOrder 重编
        assertEquals(LocalTime.parse("07:00"), blocks.get(0).getStartTime());
        assertEquals(0, blocks.get(0).getSortOrder());
        assertEquals("h1", blocks.get(1).getHabitId()); // 排序后: 07 背单词 / 09 晨跑 / 12 午休
        assertEquals("s1", blocks.get(0).getSubjectId());
        assertEquals("c1", blocks.get(0).getChapterId());
        blocks.forEach(blk -> {
            assertEquals("PROPOSED", blk.getStatus());
            assertEquals("AI", blk.getSource());
        });
    }

    @Test
    void hallucinatedIds_resetToNull() {
        List<PlanBlock> blocks = parse(draft(
                b("09:00", "10:00", "跑步", "HABIT", "fake-habit-id", null, null),
                b("10:00", "11:00", "学习", "STUDY", null, "fake-subject", "fake-chapter"),
                b("11:00", "12:00", "复习", "STUDY", "h2", "s1", "c1")));
        assertNull(blocks.get(0).getHabitId());
        assertNull(blocks.get(1).getSubjectId());
        assertNull(blocks.get(1).getChapterId());
        assertEquals("h2", blocks.get(2).getHabitId());
        assertEquals("s1", blocks.get(2).getSubjectId());
    }

    @Test
    void invalidOrReversedTimes_droppedNotWholeFailed() {
        List<PlanBlock> blocks = parse(draft(
                b("25:00", "26:00", "坏时间", "HABIT", null, null, null),
                b("10:30", "10:00", "时间反转", "HABIT", null, null, null),
                b("", "11:00", "空时间", "HABIT", null, null, null),
                b("08:00", "09:00", "正常1", "HABIT", "h1", null, null),
                b("09:00", "10:00", "正常2", "REST", null, null, null),
                b("11:00", "12:00", "正常3", "OTHER", null, null, null)));
        assertEquals(3, blocks.size());
        assertTrue(blocks.stream().noneMatch(x -> x.getTitle().equals("坏时间")));
        assertTrue(blocks.stream().noneMatch(x -> x.getTitle().equals("时间反转")));
    }

    @Test
    void emptyBlocks_throwsResponseInvalid() {
        assertEquals(6005, codeOf(() -> parse(new PlanDraft(List.of()))));
        assertEquals(6005, codeOf(() -> parse(new PlanDraft(null))));
    }

    @Test
    void longTitle_truncatedTo30() {
        String longTitle = "习".repeat(40);
        List<PlanBlock> blocks = parse(draft(
                b("08:00", "09:00", longTitle, "HABIT", null, null, null),
                b("09:00", "10:00", "乙", "REST", null, null, null),
                b("10:00", "11:00", "丙", "OTHER", null, null, null)));
        assertEquals(30, blocks.get(0).getTitle().length());
    }

    @Test
    void typeOutsideWhitelist_fallsBackToOther() {
        List<PlanBlock> blocks = parse(draft(
                b("08:00", "09:00", "健身", "WORKOUT", null, null, null),
                b("09:00", "10:00", "习惯小写", "habit", "h1", null, null),
                b("10:00", "11:00", "休息", null, null, null, null)));
        assertEquals("OTHER", blocks.get(0).getBlockType());
        assertEquals("HABIT", blocks.get(1).getBlockType()); // 大小写归一
        assertEquals("OTHER", blocks.get(2).getBlockType());
    }

    @Test
    void moreThanMaxBlocks_truncatedByTime() {
        PlanDraftBlock[] many = new PlanDraftBlock[15];
        for (int i = 0; i < 15; i++) {
            many[i] = b(String.format("%02d:00", 7 + i), String.format("%02d:30", 7 + i), "块" + i, "OTHER", null, null, null);
        }
        List<PlanBlock> blocks = parse(draft(many));
        assertEquals(MAX, blocks.size());
        assertEquals(LocalTime.parse("07:00"), blocks.get(0).getStartTime());
        assertEquals(LocalTime.parse("18:00"), blocks.get(11).getStartTime()); // 12 块: 07..18
    }

    @Test
    void duplicateStartTime_keepFirst() {
        List<PlanBlock> blocks = parse(draft(
                b("09:00", "10:00", "先来的", "HABIT", "h1", null, null),
                b("09:00", "11:00", "重复的", "REST", null, null, null),
                b("10:00", "11:30", "另一个", "STUDY", null, "s1", null),
                b("11:30", "12:00", "第三", "OTHER", null, null, null)));
        assertEquals(3, blocks.size());
        assertEquals("先来的", blocks.stream().filter(x -> x.getStartTime().equals(LocalTime.parse("09:00")))
                .findFirst().orElseThrow().getTitle());
        assertTrue(blocks.stream().noneMatch(x -> x.getTitle().equals("重复的")));
    }

    @Test
    void fewerThanMinBlocks_throws6005() {
        assertEquals(6005, codeOf(() -> parse(draft(
                b("08:00", "09:00", "只有一块", "HABIT", null, null, null),
                b("09:00", "10:00", "坏时间", "REST", null, null, null),
                b("10:30", "10:00", "反转", "OTHER", null, null, null))))); // 仅 1 条有效
    }

    @Test
    void nullEntriesInArray_areSkipped() {
        List<PlanBlock> blocks = PlanJsonParser.parse(
                new PlanDraft(java.util.Arrays.asList(
                        b("08:00", "09:00", "甲", "HABIT", null, null, null),
                        null,
                        b("09:00", "10:00", "乙", "REST", null, null, null),
                        b("10:00", "11:00", "丙", "OTHER", null, null, null))),
                HABITS, SUBJECTS, CHAPTERS, MIN, MAX);
        assertEquals(3, blocks.size());
    }
}
