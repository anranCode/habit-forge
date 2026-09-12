package com.habitforge.modules.study.service;

import com.habitforge.modules.study.dto.FlashcardCreateRequest;
import com.habitforge.modules.study.dto.FlashcardQueueResponse;
import com.habitforge.modules.study.dto.FlashcardResponse;
import com.habitforge.modules.study.dto.FlashcardReviewResultResponse;
import com.habitforge.modules.study.dto.FlashcardStatsResponse;
import com.habitforge.modules.study.dto.FlashcardUpdateRequest;

public interface FlashcardService {

    /** subjectId 归属校验 7001; chapterId 传了校验 7002; 初始 EF=2.50, due=今天 */
    FlashcardResponse create(String userId, FlashcardCreateRequest request);

    /** 到期队列: due&lt;=today AND ACTIVE, 按 due_date,created_at 排序; subjectId 可选过滤 */
    FlashcardQueueResponse queue(String userId, String subjectId, Integer limit);

    /** 评分: rating 白名单 7005, 卡归属 7004; 写日志快照; 当日≥5 张未发过 → +10 分一次 */
    FlashcardReviewResultResponse review(String userId, String cardId, Integer rating);

    FlashcardStatsResponse stats(String userId);

    /** 各字段 null=不改; 换科目/章节重新校验归属 */
    FlashcardResponse update(String userId, String cardId, FlashcardUpdateRequest request);

    /** 物理删除(复习日志 FK CASCADE) */
    void delete(String userId, String cardId);
}
