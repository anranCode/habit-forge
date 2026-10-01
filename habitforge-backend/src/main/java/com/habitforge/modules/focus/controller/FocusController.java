package com.habitforge.modules.focus.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.focus.dto.FocusLimitRequest;
import com.habitforge.modules.focus.dto.FocusLogRequest;
import com.habitforge.modules.focus.dto.FocusLogResponse;
import com.habitforge.modules.focus.dto.FocusTrendResponse;
import com.habitforge.modules.focus.dto.UrgeRequest;
import com.habitforge.modules.focus.service.FocusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 注意力/节制（P2 手机节制）：娱乐时长、冲动抵抗、每日上限
 */
@RestController
@RequestMapping("/api/v1/focus")
@RequiredArgsConstructor
public class FocusController {

    private final FocusService focusService;

    /** 今日注意力状态（含达标判定与当前上限） */
    @GetMapping("/today")
    public Result<FocusLogResponse> today() {
        return Result.success(focusService.today(SecurityUtils.getCurrentUserId()));
    }

    /** 录入/修改某日娱乐时长（不传 logDate = 今天；达标状态变化会结算积分） */
    @PutMapping("/logs")
    public Result<FocusLogResponse> saveLog(@Valid @RequestBody FocusLogRequest request) {
        return Result.success(focusService.saveLog(SecurityUtils.getCurrentUserId(), request), "已记录");
    }

    /** 记一次「想刷手机」的冲动（忍住/没忍住都记，只记今天） */
    @PostMapping("/urges")
    public Result<FocusLogResponse> urge(@Valid @RequestBody UrgeRequest request) {
        return Result.success(focusService.recordUrge(SecurityUtils.getCurrentUserId(), request),
                Boolean.TRUE.equals(request.getResisted()) ? "记下了，你忍住了 👍" : "记下了，明天再来一次");
    }

    /** 区间趋势（没录入的日子 minutes 为 null） */
    @GetMapping("/trend")
    public Result<FocusTrendResponse> trend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(focusService.trend(SecurityUtils.getCurrentUserId(), from, to));
    }

    /** 设置每日娱乐时长上限（分钟） */
    @PutMapping("/limit")
    public Result<FocusLogResponse> updateLimit(@Valid @RequestBody FocusLimitRequest request) {
        return Result.success(focusService.updateLimit(SecurityUtils.getCurrentUserId(), request.getLimitMinutes()),
                "上限已更新");
    }
}
