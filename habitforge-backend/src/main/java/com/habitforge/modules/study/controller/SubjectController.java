package com.habitforge.modules.study.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.SubjectCreateRequest;
import com.habitforge.modules.study.dto.SubjectResponse;
import com.habitforge.modules.study.dto.SubjectUpdateRequest;
import com.habitforge.modules.study.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @PostMapping
    public Result<SubjectResponse> create(@Valid @RequestBody SubjectCreateRequest request) {
        return Result.success(subjectService.create(SecurityUtils.getCurrentUserId(), request), "科目创建成功");
    }

    /** 我的科目列表(含实时章节汇总/倒计时) */
    @GetMapping
    public Result<List<SubjectResponse>> list() {
        return Result.success(subjectService.listMine(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public Result<SubjectResponse> detail(@PathVariable String id) {
        return Result.success(subjectService.getDetail(SecurityUtils.getCurrentUserId(), id));
    }

    /** 各字段可选, null 表示不修改 */
    @PutMapping("/{id}")
    public Result<SubjectResponse> update(@PathVariable String id, @Valid @RequestBody SubjectUpdateRequest request) {
        return Result.success(subjectService.update(SecurityUtils.getCurrentUserId(), id, request), "更新成功");
    }

    /** 逻辑删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        subjectService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "删除成功");
    }
}
