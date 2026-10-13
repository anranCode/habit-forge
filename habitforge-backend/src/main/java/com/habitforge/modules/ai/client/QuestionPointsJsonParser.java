package com.habitforge.modules.ai.client;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.ai.dto.QuestionPointsDraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 考点识别草稿清洗（纯静态可单测, 沿 PlanJsonParser 防幻觉丢弃模式）：
 * trim → 去空白项 → 截 maxChars 字 → LinkedHashSet 去重保序 → 超 maxPoints 截断;
 * 有效考点 < 1 视为解析失败(6005)。阈值由调用方从 AiProperties 传入, 与手工录入侧
 * PointsCodec(normalize) 的校验语义一致（那边是拒 400, 这边是清洗模型输出）。
 */
public final class QuestionPointsJsonParser {

    private QuestionPointsJsonParser() {
    }

    public static List<String> parse(QuestionPointsDraft draft, int maxPoints, int maxChars) {
        if (draft == null || draft.points() == null || draft.points().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String raw : draft.points()) {
            if (raw == null) {
                continue;
            }
            String p = raw.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > maxChars) {
                p = p.substring(0, maxChars);
            }
            kept.add(p);
            if (kept.size() >= maxPoints) {
                break; // 话痨输出截断在配置上限, 别放大清单噪音
            }
        }
        if (kept.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        return new ArrayList<>(kept);
    }
}
