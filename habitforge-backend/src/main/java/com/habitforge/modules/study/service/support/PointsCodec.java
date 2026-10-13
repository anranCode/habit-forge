package com.habitforge.modules.study.service.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * questions.knowledge_points（JSON 字符串数组）的编解码 + 写入校验 ——
 * 仿 options 惯例：DTO 层传 List<String>，库里存 JSON 串，不走 TypeHandler。
 * 两个 service（题目 CRUD / 错题本回显）共用，阈值只写在这一处。
 */
public final class PointsCodec {

    /** 单题考点数上限（P6 的 AiProperties 有同口径配置，此处是手动录入侧的硬兜底） */
    public static final int MAX_POINTS = 8;

    /** 单个考点字数上限 */
    public static final int MAX_POINT_CHARS = 20;

    private PointsCodec() {
    }

    /**
     * trim → 去空白项 → 去重保序 → 上限校验。
     * null 原样返回 null（= 不改 / 未传）；全空白入参归一成空列表（= 清空），不会漏过校验直接落库。
     */
    public static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return null;
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String point : raw) {
            if (StrUtil.isBlank(point)) {
                continue;
            }
            String trimmed = point.trim();
            if (trimmed.length() > MAX_POINT_CHARS) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "考点「" + trimmed + "」超过 " + MAX_POINT_CHARS + " 字");
            }
            kept.add(trimmed);
        }
        if (kept.size() > MAX_POINTS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单题考点最多 " + MAX_POINTS + " 个");
        }
        return new ArrayList<>(kept);
    }

    /** List → 入库 JSON 串；null/空集合一律存 NULL（"未标注"与"已清空"不区分，也就不需要区分） */
    public static String toStored(List<String> points) {
        return points == null || points.isEmpty() ? null : JSONUtil.toJsonStr(points);
    }

    /** 入库串 → List；空白返回 null。畸形串同样按未标注处理并告警 —— 不让一行脏数据炸掉整个列表接口 */
    public static List<String> parse(String storedJson) {
        if (StrUtil.isBlank(storedJson)) {
            return null;
        }
        try {
            List<String> points = JSONUtil.toList(storedJson, String.class);
            return points.isEmpty() ? null : points;
        } catch (Exception e) {
            return null;
        }
    }

    /** 整元素 LIKE 模式的构造：考点 x → "x"（带引号），防「函数」误命中「复合函数」 */
    public static String likeElement(String point) {
        return "\"" + point.trim() + "\"";
    }
}
