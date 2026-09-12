package com.habitforge.modules.study.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.ChapterCreateRequest;
import com.habitforge.modules.study.dto.ChapterResponse;
import com.habitforge.modules.study.dto.ChapterStatusRequest;
import com.habitforge.modules.study.dto.ChapterUpdateRequest;
import com.habitforge.modules.study.service.ChapterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    @PostMapping
    public Result<ChapterResponse> create(@Valid @RequestBody ChapterCreateRequest request) {
        return Result.success(chapterService.create(SecurityUtils.getCurrentUserId(), request), "章节创建成功");
    }

    /** 某科目全部章节(平铺含所有层级, 前端按 parentId 组树) */
    @GetMapping("/subject/{subjectId}")
    public Result<List<ChapterResponse>> listBySubject(@PathVariable String subjectId) {
        return Result.success(chapterService.listBySubject(SecurityUtils.getCurrentUserId(), subjectId));
    }

    /** 改名/换父(防环 7003)/排序 */
    @PutMapping("/{id}")
    public Result<ChapterResponse> update(@PathVariable String id, @Valid @RequestBody ChapterUpdateRequest request) {
        return Result.success(chapterService.update(SecurityUtils.getCurrentUserId(), id, request), "更新成功");
    }

    /** 状态三态切换: DONE 级联子孙, 首次完成 +20 积分 */
    @PatchMapping("/{id}/status")
    public Result<ChapterResponse> updateStatus(@PathVariable String id, @Valid @RequestBody ChapterStatusRequest request) {
        return Result.success(chapterService.updateStatus(SecurityUtils.getCurrentUserId(), id, request.getStatus()), "更新成功");
    }

    /** 物理删除(DB FK CASCADE 连带删除子树) */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        chapterService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "删除成功");
    }
}
