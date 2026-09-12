package com.habitforge.modules.study.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.QuestionCreateRequest;
import com.habitforge.modules.study.dto.QuestionResponse;
import com.habitforge.modules.study.dto.QuestionUpdateRequest;
import com.habitforge.modules.study.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public Result<QuestionResponse> create(@Valid @RequestBody QuestionCreateRequest request) {
        return Result.success(questionService.create(SecurityUtils.getCurrentUserId(), request), "题目创建成功");
    }

    /** 分页查询(page 默认 1, size 默认 20) */
    @GetMapping
    public Result<Page<QuestionResponse>> page(@RequestParam(required = false) String subjectId,
                                               @RequestParam(required = false) String chapterId,
                                               @RequestParam(required = false) String questionType,
                                               @RequestParam(required = false) String sourceType,
                                               @RequestParam(required = false) Integer difficulty,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return Result.success(questionService.page(SecurityUtils.getCurrentUserId(),
                subjectId, chapterId, questionType, sourceType, difficulty, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<QuestionResponse> detail(@PathVariable String id) {
        return Result.success(questionService.getDetail(SecurityUtils.getCurrentUserId(), id));
    }

    /** 各字段可选, null 表示不修改 */
    @PutMapping("/{id}")
    public Result<QuestionResponse> update(@PathVariable String id, @Valid @RequestBody QuestionUpdateRequest request) {
        return Result.success(questionService.update(SecurityUtils.getCurrentUserId(), id, request), "更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        questionService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "删除成功");
    }
}
