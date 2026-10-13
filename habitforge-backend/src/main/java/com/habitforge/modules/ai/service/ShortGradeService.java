package com.habitforge.modules.ai.service;

import com.habitforge.modules.ai.dto.ShortGradeOutcome;
import com.habitforge.modules.study.entity.Question;

import java.time.LocalDate;

/**
 * 主观题 AI 判分（拆要点判 hit → 服务端按阈值换算对错 → question_grades 留痕）。
 * 护栏与语义与 AiScheduleService 同构: 成败均写流水(kind=GRADE), 上游失败退额度、解析失败不退。
 */
public interface ShortGradeService {

    /**
     * @param question   已校验归属的题目（调用方加载, 本方法不查库）
     * @param userAnswer 学生作答（调用方已 trim; 空白直接 400, 不触 AI 不耗额度）
     * @throws BusinessException 6001 未启用 / 400 空作答 / 7014 超限 / 6003 判卷锁占用 /
     *                           6004 上游或留痕失败 / 6005 返回不可解析
     */
    ShortGradeOutcome grade(String userId, Question question, String userAnswer);

    /** 判分额度键: 与排程生成(ai:plan)/周报隔离, 日粒度 */
    static String quotaKey(String userId, LocalDate date) {
        return "ai:grade:" + userId + ":" + date;
    }
}
