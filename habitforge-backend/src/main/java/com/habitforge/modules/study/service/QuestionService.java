package com.habitforge.modules.study.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.habitforge.modules.study.dto.PracticeQuestionResponse;
import com.habitforge.modules.study.dto.QuestionCreateRequest;
import com.habitforge.modules.study.dto.QuestionResponse;
import com.habitforge.modules.study.dto.QuestionUpdateRequest;

import java.util.List;

public interface QuestionService {

    QuestionResponse create(String userId, QuestionCreateRequest request);

    /** 分页查询(page 默认1, size 默认20), 条件 subjectId/chapterId/questionType/sourceType/difficulty/keyword(stem LIKE)/knowledgePoint(整元素 LIKE), createdAt desc */
    Page<QuestionResponse> page(String userId, String subjectId, String chapterId, String questionType,
                                String sourceType, Integer difficulty, String keyword, String knowledgePoint,
                                long page, long size);

    /** 考点去重清单(题库筛选下拉选项源), subjectId 空=全库; 按首次出现顺序 */
    List<String> knowledgePoints(String userId, String subjectId);

    /**
     * 随机取题(刷题会话开头): 范围=全库/科目/章节, questionTypes 逗号集(空=全部题型),
     * limit 夹到 [1, PRACTICE_FETCH_MAX]。列裁剪使 answer 不出库; 空结果 7015; Redis 每分钟限流。
     */
    List<PracticeQuestionResponse> randomQuestions(String userId, String subjectId, String chapterId,
                                                   String questionTypes, Integer limit);

    QuestionResponse getDetail(String userId, String id);

    /** 各字段 null=不改 */
    QuestionResponse update(String userId, String id, QuestionUpdateRequest request);

    /** 物理删除(错题行 FK CASCADE 连带) */
    void delete(String userId, String id);
}
