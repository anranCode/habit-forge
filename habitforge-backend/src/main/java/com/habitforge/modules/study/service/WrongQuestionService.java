package com.habitforge.modules.study.service;

import com.habitforge.modules.study.dto.WrongQuestionResponse;

import java.util.List;

public interface WrongQuestionService {

    /** 加入错题本(upsert: 已有则 wrong_count+1 / streak 清零 / mastered=0) */
    WrongQuestionResponse add(String userId, String questionId);

    /** 待复习错题(mastered=0), wrong_count desc + last_wrong_at asc, 附题目详情 */
    List<WrongQuestionResponse> list(String userId, String subjectId, Integer limit);

    /** 重练登记: 答对 streak+1 达阈值自动 mastered; 答错 count+1 且 streak/mastered 重置 */
    WrongQuestionResponse practice(String userId, String questionId, boolean correct);

    /** 手动摘除/恢复 */
    WrongQuestionResponse setMastered(String userId, String questionId, boolean mastered);
}
