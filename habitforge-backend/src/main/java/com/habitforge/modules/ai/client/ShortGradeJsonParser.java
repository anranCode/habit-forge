package com.habitforge.modules.ai.client;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.ai.dto.ShortGradeDraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 判分草稿的业务校验层（纯静态可单测, 沿 PlanJsonParser 的防幻觉丢弃模式）：
 * point 空白丢弃、hit 非布尔(null)丢弃（不当 false 蒙判——宁可重判一次也不给用户记错账）、
 * 同 text 去重保先、超 50 字截断（与 prompt 的"≤50 字"口径同源）、超 8 条截断；
 * 有效要点 < 1 视为解析失败(6005)。
 */
public final class ShortGradeJsonParser {

    private ShortGradeJsonParser() {
    }

    /** 与 ShortGradePromptTemplate SYSTEM 的"要点数量 1–8 个、每条 ≤50 字"一致, 改一处要两处同步 */
    static final int MAX_POINTS = 8;
    static final int MAX_POINT_CHARS = 50;

    public static List<ShortGradeDraft.PointDraft> parse(ShortGradeDraft draft) {
        if (draft == null || draft.points() == null || draft.points().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        List<ShortGradeDraft.PointDraft> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ShortGradeDraft.PointDraft p : draft.points()) {
            if (p == null || p.hit() == null) {
                continue;
            }
            String point = p.point() == null ? "" : p.point().strip();
            if (point.isEmpty()) {
                continue;
            }
            if (point.length() > MAX_POINT_CHARS) {
                point = point.substring(0, MAX_POINT_CHARS);
            }
            if (!seen.add(point)) {
                continue; // 同文要点去重, 保留先出现者
            }
            valid.add(new ShortGradeDraft.PointDraft(point, p.hit()));
            if (valid.size() == MAX_POINTS) {
                break; // 超 8 条截断: 模型话痨不该放大 token 成本与清单噪音
            }
        }
        if (valid.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        return valid;
    }

    /** covered = hit 为 true 的要点数（换算口径与对错判定同源, 供 service 与展示共用） */
    public static int countCovered(List<ShortGradeDraft.PointDraft> points) {
        return (int) points.stream().filter(p -> Boolean.TRUE.equals(p.hit())).count();
    }
}
