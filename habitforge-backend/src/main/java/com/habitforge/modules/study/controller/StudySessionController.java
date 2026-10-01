package com.habitforge.modules.study.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.DailyStudyTimeResponse;
import com.habitforge.modules.study.dto.StudySessionManualRequest;
import com.habitforge.modules.study.dto.StudySessionResponse;
import com.habitforge.modules.study.dto.StudySessionStartRequest;
import com.habitforge.modules.study.dto.StudyTimeSummaryResponse;
import com.habitforge.modules.study.service.StudySessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习时长（P0）。与 StudyController 共用 /api/v1/study 前缀, 路径不重叠
 */
@RestController
@RequestMapping("/api/v1/study")
@RequiredArgsConstructor
public class StudySessionController {

    private final StudySessionService studySessionService;

    /** 开始计时（已有进行中的返 7010） */
    @PostMapping("/sessions/start")
    public Result<StudySessionResponse> start(@Valid @RequestBody StudySessionStartRequest request) {
        return Result.success(studySessionService.start(SecurityUtils.getCurrentUserId(), request), "开始学习");
    }

    /** 结束计时并回填分钟 */
    @PostMapping("/sessions/{id}/end")
    public Result<StudySessionResponse> end(@PathVariable String id) {
        return Result.success(studySessionService.end(SecurityUtils.getCurrentUserId(), id), "已结束, 本次时长已记录");
    }

    /** 手动补录（漏计时/离线学习） */
    @PostMapping("/sessions")
    public Result<StudySessionResponse> manual(@Valid @RequestBody StudySessionManualRequest request) {
        return Result.success(studySessionService.manual(SecurityUtils.getCurrentUserId(), request), "补录成功");
    }

    /** 进行中的计时（未开始返 data=null; 前端刷新后据此恢复计时） */
    @GetMapping("/sessions/active")
    public Result<StudySessionResponse> active() {
        return Result.success(studySessionService.active(SecurityUtils.getCurrentUserId()));
    }

    /** 某日全部记录（date 不传 = 今天） */
    @GetMapping("/sessions")
    public Result<List<StudySessionResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(studySessionService.listByDate(SecurityUtils.getCurrentUserId(), date));
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> delete(@PathVariable String id) {
        studySessionService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "已删除");
    }

    /** 某日学习时长汇总（date 不传 = 今天） */
    @GetMapping("/time/summary")
    public Result<StudyTimeSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(studySessionService.summary(SecurityUtils.getCurrentUserId(), date));
    }

    /** 区间内每日时长（无记录的日子补 0） */
    @GetMapping("/time/daily")
    public Result<List<DailyStudyTimeResponse>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(studySessionService.daily(SecurityUtils.getCurrentUserId(), from, to));
    }
}
