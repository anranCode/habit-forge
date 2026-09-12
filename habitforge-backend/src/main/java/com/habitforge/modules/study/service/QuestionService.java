package com.habitforge.modules.study.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.habitforge.modules.study.dto.QuestionCreateRequest;
import com.habitforge.modules.study.dto.QuestionResponse;
import com.habitforge.modules.study.dto.QuestionUpdateRequest;

public interface QuestionService {

    QuestionResponse create(String userId, QuestionCreateRequest request);

    /** 分页查询(page 默认1, size 默认20), 条件 subjectId/chapterId/questionType/sourceType/difficulty/keyword(stem LIKE), createdAt desc */
    Page<QuestionResponse> page(String userId, String subjectId, String chapterId, String questionType,
                                String sourceType, Integer difficulty, String keyword, long page, long size);

    QuestionResponse getDetail(String userId, String id);

    /** 各字段 null=不改 */
    QuestionResponse update(String userId, String id, QuestionUpdateRequest request);

    /** 物理删除(错题行 FK CASCADE 连带) */
    void delete(String userId, String id);
}
