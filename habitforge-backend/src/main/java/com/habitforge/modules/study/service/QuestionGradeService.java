package com.habitforge.modules.study.service;

import com.habitforge.modules.study.dto.GradeRequest;
import com.habitforge.modules.study.dto.GradeResponse;

public interface QuestionGradeService {

    /**
     * 统一判分管线（刷题 + 错题重练共用）：
     * 客观题 AnswerMatcher 秒判；SHORT 走 AI（P4 接入，当前报"尚未开放"）。
     * 副作用规则：答错 → add() upsert 入本；答对且在本未摘 → practice(true) 累计连对；
     * AI 失败路径绝不写错题本（未判分 ≠ 答错）。
     */
    GradeResponse grade(String userId, GradeRequest request);
}
