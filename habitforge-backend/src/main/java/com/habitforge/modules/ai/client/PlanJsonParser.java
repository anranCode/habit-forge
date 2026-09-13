package com.habitforge.modules.ai.client;

import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.ai.dto.PlanDraft;
import com.habitforge.modules.ai.entity.PlanBlock;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI 草稿的业务校验层（JSON 提取/反序列化已由 BeanOutputConverter 完成, 本类纯静态可单测）：
 * HH:mm 解析、start<end、type 白名单外→OTHER、habitId/subjectId/chapterId ∉ 候选集→null（防幻觉白名单）、
 * title 截 30、按 start 去重、超 maxBlocks 截断；单条不合格丢弃不整体失败；有效块 < minBlocks 抛 6005
 */
public final class PlanJsonParser {

    private PlanJsonParser() {
    }

    private static final Set<String> BLOCK_TYPES = Set.of("HABIT", "STUDY", "REST", "OTHER");

    public static List<PlanBlock> parse(PlanDraft draft,
                                        Set<String> habitIdCandidates,
                                        Set<String> subjectIdCandidates,
                                        Set<String> chapterIdCandidates,
                                        int minBlocks,
                                        int maxBlocks) {
        if (draft == null || draft.blocks() == null || draft.blocks().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        List<PlanBlock> valid = new ArrayList<>();
        Set<LocalTime> seenStart = new HashSet<>();
        for (PlanDraft.PlanDraftBlock b : draft.blocks()) {
            if (b == null) {
                continue;
            }
            LocalTime start = parseTime(b.start());
            LocalTime end = parseTime(b.end());
            if (start == null || end == null || !start.isBefore(end)) {
                continue; // 时间非法或反转: 丢弃该条
            }
            if (!seenStart.add(start)) {
                continue; // 按 start 时间去重, 保留先出现者
            }
            String title = b.title() == null ? "" : b.title().strip();
            if (title.isEmpty()) {
                continue;
            }
            if (title.length() > AppConstant.PLAN_TITLE_MAX_CHARS) {
                title = title.substring(0, AppConstant.PLAN_TITLE_MAX_CHARS);
            }
            PlanBlock block = new PlanBlock();
            block.setBlockType(normalizeType(b.type()));
            block.setTitle(title);
            block.setStartTime(start);
            block.setEndTime(end);
            block.setHabitId(whitelist(b.habitId(), habitIdCandidates));
            block.setSubjectId(whitelist(b.subjectId(), subjectIdCandidates));
            block.setChapterId(whitelist(b.chapterId(), chapterIdCandidates));
            block.setStatus("PROPOSED");
            block.setSource("AI");
            block.setSortOrder(0);
            valid.add(block);
        }
        valid.sort(Comparator.comparing(PlanBlock::getStartTime));
        if (valid.size() > maxBlocks) {
            valid = new ArrayList<>(valid.subList(0, maxBlocks));
        }
        if (valid.size() < minBlocks) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        for (int i = 0; i < valid.size(); i++) {
            valid.get(i).setSortOrder(i);
        }
        return valid;
    }

    /** HH:mm / HH:mm:ss 均可, 非法返回 null */
    private static LocalTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(text.strip().toUpperCase(Locale.ROOT));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return "OTHER";
        }
        String t = type.strip().toUpperCase(Locale.ROOT);
        return BLOCK_TYPES.contains(t) ? t : "OTHER";
    }

    /** 防幻觉白名单: ∉ 候选集（含空串）→ null */
    private static String whitelist(String id, Set<String> candidates) {
        if (id == null || id.isBlank() || candidates == null || !candidates.contains(id.strip())) {
            return null;
        }
        return id.strip();
    }
}
