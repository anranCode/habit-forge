package com.habitforge.modules.study.service;

import com.habitforge.modules.study.dto.ChapterCreateRequest;
import com.habitforge.modules.study.dto.ChapterResponse;
import com.habitforge.modules.study.dto.ChapterUpdateRequest;

import java.util.List;

public interface ChapterService {

    ChapterResponse create(String userId, ChapterCreateRequest request);

    /** 某科目全部章节(平铺含所有层级, 前端按 parentId 组树) */
    List<ChapterResponse> listBySubject(String userId, String subjectId);

    /** 改名/换父(防环 7003)/排序; null 字段不修改 */
    ChapterResponse update(String userId, String chapterId, ChapterUpdateRequest request);

    /** 变更状态: 置 DONE 级联子孙; 首次 非DONE→DONE 加 20 积分; 改回非 DONE 仅清 doneAt 不回收积分 */
    ChapterResponse updateStatus(String userId, String chapterId, String status);

    /** 物理删除, DB FK CASCADE 连带删除整棵子树 */
    void delete(String userId, String chapterId);
}
