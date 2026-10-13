package com.habitforge.modules.study.service;

import com.habitforge.modules.study.dto.WrongQuestionResponse;

import java.util.List;

public interface WrongQuestionService {

    /** 加入错题本(upsert: 已有则 wrong_count+1 / streak 清零 / mastered=0) */
    WrongQuestionResponse add(String userId, String questionId);

    /** 待复习错题(mastered=0), wrong_count desc + last_wrong_at asc, 附题目详情（reveal=true） */
    List<WrongQuestionResponse> list(String userId, String subjectId, Integer limit);

    /**
     * reveal=false: 每题 answer/analysis 置 null —— 重练队列专用(判分前答案不出库, 计划决策 2)。
     * 管理端错题本列表应继续用 reveal=true(要看得见答案讲题)。
     */
    List<WrongQuestionResponse> list(String userId, String subjectId, Integer limit, boolean reveal);

    /**
     * 重练登记: 答对 streak+1 达阈值自动 mastered; 答错 count+1 且 streak/mastered 重置。
     * 客户端自评端点已在 P5 拆除 —— 现在是 QuestionGradeService 判分后的内部原语。
     */
    WrongQuestionResponse practice(String userId, String questionId, boolean correct);

    /** 手动摘除/恢复 */
    WrongQuestionResponse setMastered(String userId, String questionId, boolean mastered);
}
