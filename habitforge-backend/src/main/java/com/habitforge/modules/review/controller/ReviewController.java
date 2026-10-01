package com.habitforge.modules.review.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.review.dto.WeeklyReportResponse;
import com.habitforge.modules.review.dto.WeeklyReportUpdateRequest;
import com.habitforge.modules.review.dto.WeeklyReportUsageResponse;
import com.habitforge.modules.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 每周 AI 复盘（date 不传 = 本周; 落在哪周就取哪周, 周一起算）
 */
@RestController
@RequestMapping("/api/v1/reviews/weekly")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 指定周报告, 未生成返 data=null（仿 /journals/today 与 /plans/today） */
    @GetMapping
    public Result<WeeklyReportResponse> week(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(reviewService.getWeek(SecurityUtils.getCurrentUserId(), date));
    }

    /** 生成/重新生成（同步阻塞, 可能数十秒） */
    @PostMapping("/generate")
    public Result<WeeklyReportResponse> generate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(reviewService.generate(SecurityUtils.getCurrentUserId(), date), "报告已生成");
    }

    /** 编辑（AI 只出初稿, 终稿归用户） */
    @PutMapping("/{id}")
    public Result<WeeklyReportResponse> update(@PathVariable String id,
                                               @Valid @RequestBody WeeklyReportUpdateRequest request) {
        return Result.success(reviewService.update(SecurityUtils.getCurrentUserId(), id, request), "已保存");
    }

    /** 本周生成额度 */
    @GetMapping("/usage")
    public Result<WeeklyReportUsageResponse> usage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(reviewService.usage(SecurityUtils.getCurrentUserId(), date));
    }
}
