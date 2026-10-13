package com.habitforge.modules.ai.service;

import java.time.LocalDate;

/**
 * 单题考点 AI 识别（覆盖写 knowledge_points; 成败均写流水 kind=POINTS ref_id=question_id）。
 * 护栏语义与 ShortGradeService/AiScheduleService 同构: 上游失败退额度、解析失败不退。
 */
public interface QuestionPointsService {

    /**
     * @throws BusinessException 6001 未启用 / 7007 题目不存在或非本人 / 7013 超限 /
     *                           6003 识别锁占用 / 6004 上游失败 / 6005 返回不可解析
     */
    void recognize(String userId, String questionId);

    /** 识别额度键: 与判分/排程/周报隔离, 日粒度 */
    static String quotaKey(String userId, LocalDate date) {
        return "ai:points:" + userId + ":" + date;
    }
}
