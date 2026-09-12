package com.habitforge.modules.study.service;

import com.habitforge.modules.study.dto.SubjectCreateRequest;
import com.habitforge.modules.study.dto.SubjectResponse;
import com.habitforge.modules.study.dto.SubjectUpdateRequest;

import java.util.List;

public interface SubjectService {

    SubjectResponse create(String userId, SubjectCreateRequest request);

    /** null 字段不修改 */
    SubjectResponse update(String userId, String subjectId, SubjectUpdateRequest request);

    /** 逻辑删除(deleted=1) */
    void delete(String userId, String subjectId);

    SubjectResponse getDetail(String userId, String subjectId);

    /** 我的科目列表(按 sortOrder, createdAt 排序, 含实时章节汇总) */
    List<SubjectResponse> listMine(String userId);
}
