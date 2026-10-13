package com.habitforge.modules.study.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.GradeRequest;
import com.habitforge.modules.study.dto.GradeResponse;
import com.habitforge.modules.study.dto.PracticeQuestionResponse;
import com.habitforge.modules.study.dto.QuestionCreateRequest;
import com.habitforge.modules.study.dto.QuestionResponse;
import com.habitforge.modules.study.dto.QuestionUpdateRequest;
import com.habitforge.modules.study.service.QuestionGradeService;
import com.habitforge.modules.study.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionGradeService questionGradeService;

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
                                               @RequestParam(required = false) String knowledgePoint,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return Result.success(questionService.page(SecurityUtils.getCurrentUserId(),
                subjectId, chapterId, questionType, sourceType, difficulty, keyword, knowledgePoint, page, size));
    }

    /**
     * 考点去重清单(题库筛选下拉选项源), subjectId 空=全库。
     * 字面量路径优先于 /{id}, 不会被 detail 抢走。
     */
    @GetMapping("/knowledge-points")
    public Result<List<String>> knowledgePoints(@RequestParam(required = false) String subjectId) {
        return Result.success(questionService.knowledgePoints(SecurityUtils.getCurrentUserId(), subjectId));
    }

    /** 随机取题(刷题会话): 范围 全库/科目/章节, questionTypes 逗号集(空=全部), limit 服务端夹到 50 */
    @GetMapping("/random")
    public Result<List<PracticeQuestionResponse>> random(@RequestParam(required = false) String subjectId,
                                                         @RequestParam(required = false) String chapterId,
                                                         @RequestParam(required = false) String questionTypes,
                                                         @RequestParam(required = false) Integer limit) {
        return Result.success(questionService.randomQuestions(SecurityUtils.getCurrentUserId(),
                subjectId, chapterId, questionTypes, limit));
    }

    /** 统一判分: 客观题服务端判, SHORT AI 判(P4); 答错自动入本、答对累计连对 */
    @PostMapping("/grade")
    public Result<GradeResponse> grade(@Valid @RequestBody GradeRequest request) {
        return Result.success(questionGradeService.grade(SecurityUtils.getCurrentUserId(), request));
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
