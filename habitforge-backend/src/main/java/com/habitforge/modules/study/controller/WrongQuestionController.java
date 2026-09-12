package com.habitforge.modules.study.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.WrongQuestionAddRequest;
import com.habitforge.modules.study.dto.WrongQuestionMasteredRequest;
import com.habitforge.modules.study.dto.WrongQuestionPracticeRequest;
import com.habitforge.modules.study.dto.WrongQuestionResponse;
import com.habitforge.modules.study.service.WrongQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wrong-questions")
@RequiredArgsConstructor
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    /** 加入错题本(upsert: 已存在则 wrong_count+1、streak 清零、mastered=0) */
    @PostMapping
    public Result<WrongQuestionResponse> add(@Valid @RequestBody WrongQuestionAddRequest request) {
        return Result.success(wrongQuestionService.add(SecurityUtils.getCurrentUserId(), request.getQuestionId()), "已加入错题本");
    }

    /** 待复习错题(mastered=0, wrong_count desc + last_wrong_at asc, 附题目详情) */
    @GetMapping
    public Result<List<WrongQuestionResponse>> list(@RequestParam(required = false) String subjectId,
                                                    @RequestParam(required = false) Integer limit) {
        return Result.success(wrongQuestionService.list(SecurityUtils.getCurrentUserId(), subjectId, limit));
    }

    /** 重练登记(自报答对/答错; 连对 2 次自动 mastered) */
    @PostMapping("/{questionId}/practice")
    public Result<WrongQuestionResponse> practice(@PathVariable String questionId,
                                                  @Valid @RequestBody WrongQuestionPracticeRequest request) {
        return Result.success(wrongQuestionService.practice(
                SecurityUtils.getCurrentUserId(), questionId, request.getCorrect()));
    }

    /** 手动摘除/恢复 */
    @PatchMapping("/{questionId}/mastered")
    public Result<WrongQuestionResponse> setMastered(@PathVariable String questionId,
                                                     @Valid @RequestBody WrongQuestionMasteredRequest request) {
        return Result.success(wrongQuestionService.setMastered(
                SecurityUtils.getCurrentUserId(), questionId, request.getMastered()), "更新成功");
    }
}
