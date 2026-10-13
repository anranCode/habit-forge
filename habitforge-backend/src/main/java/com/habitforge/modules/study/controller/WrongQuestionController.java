package com.habitforge.modules.study.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.WrongQuestionAddRequest;
import com.habitforge.modules.study.dto.WrongQuestionMasteredRequest;
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

    /**
     * 待复习错题(mastered=0, wrong_count desc + last_wrong_at asc, 附题目详情)。
     * reveal=false → 每题 answer/analysis 为 null: 重练队列专用, 对错只能由 POST /questions/grade 判定。
     */
    @GetMapping
    public Result<List<WrongQuestionResponse>> list(@RequestParam(required = false) String subjectId,
                                                    @RequestParam(required = false) Integer limit,
                                                    @RequestParam(required = false, defaultValue = "true") boolean reveal) {
        return Result.success(wrongQuestionService.list(SecurityUtils.getCurrentUserId(), subjectId, limit, reveal));
    }

    /** 手动摘除/恢复 */
    @PatchMapping("/{questionId}/mastered")
    public Result<WrongQuestionResponse> setMastered(@PathVariable String questionId,
                                                     @Valid @RequestBody WrongQuestionMasteredRequest request) {
        return Result.success(wrongQuestionService.setMastered(
                SecurityUtils.getCurrentUserId(), questionId, request.getMastered()), "更新成功");
    }
}
