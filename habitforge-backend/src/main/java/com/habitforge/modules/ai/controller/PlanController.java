package com.habitforge.modules.ai.controller;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.ai.dto.BlockCompleteRequest;
import com.habitforge.modules.ai.dto.BlockCreateRequest;
import com.habitforge.modules.ai.dto.BlockUpdateRequest;
import com.habitforge.modules.ai.dto.FreeSlotSaveRequest;
import com.habitforge.modules.ai.dto.PlanBlockResponse;
import com.habitforge.modules.ai.dto.PlanFreeSlotResponse;
import com.habitforge.modules.ai.dto.PlanResponse;
import com.habitforge.modules.ai.dto.PlanUsageResponse;
import com.habitforge.modules.ai.service.AiScheduleService;
import com.habitforge.modules.ai.service.DailyPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 今日安排（路径/字段与前端 src/api/modules/plan.ts 契约逐字一致）
 * 注意: /today、/usage、/generate 等字面量优先于 /{date} 模板匹配
 */
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final DailyPlanService dailyPlanService;
    private final AiScheduleService aiScheduleService;

    // ============ 生成 / 查询 ============

    /** 生成/重新生成今日安排（同步阻塞调 LLM, 前端超时放宽 120s） */
    @PostMapping("/generate")
    public Result<PlanResponse> generate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return Result.success(aiScheduleService.generate(SecurityUtils.getCurrentUserId(), target), "今日安排已生成");
    }

    /** 今日计划（未生成返回 data=null, 仿 /journals/today） */
    @GetMapping("/today")
    public Result<PlanResponse> today() {
        return Result.success(dailyPlanService.getPlanResponse(SecurityUtils.getCurrentUserId(), LocalDate.now()));
    }

    /** 今日生成用量（字面量路由, 优先于 /{date}） */
    @GetMapping("/usage")
    public Result<PlanUsageResponse> usage() {
        return Result.success(dailyPlanService.getUsage(SecurityUtils.getCurrentUserId()));
    }

    /** 按日期查历史计划 yyyy-MM-dd（不存在 6011） */
    @GetMapping("/{date}")
    public Result<PlanResponse> byDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PlanResponse plan = dailyPlanService.getPlanResponse(SecurityUtils.getCurrentUserId(), date);
        if (plan == null) {
            throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
        }
        return Result.success(plan);
    }

    // ============ 空闲时段 ============

    /** 整体覆盖式保存（事务内先删后插; 违者 6014） */
    @PutMapping("/free-slots")
    public Result<List<PlanFreeSlotResponse>> saveFreeSlots(@Valid @RequestBody FreeSlotSaveRequest request) {
        LocalDate date = request.getDate() == null ? LocalDate.now() : request.getDate();
        return Result.success(dailyPlanService.saveFreeSlots(
                SecurityUtils.getCurrentUserId(), date, request), "空闲时段已保存");
    }

    // ============ 采纳 / 块 CRUD ============

    /** 一键采纳全部 PROPOSED 块, data=采纳条数 */
    @PostMapping("/adopt")
    public Result<Integer> adopt(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return Result.success(dailyPlanService.adoptAll(SecurityUtils.getCurrentUserId(), target));
    }

    /** 手动加块（创建即 ADOPTED） */
    @PostMapping("/blocks")
    public Result<PlanBlockResponse> createBlock(@Valid @RequestBody BlockCreateRequest request) {
        return Result.success(dailyPlanService.createBlock(SecurityUtils.getCurrentUserId(), request), "已添加");
    }

    @PutMapping("/blocks/{id}")
    public Result<PlanBlockResponse> updateBlock(@PathVariable String id,
                                                 @Valid @RequestBody BlockUpdateRequest request) {
        return Result.success(dailyPlanService.updateBlock(SecurityUtils.getCurrentUserId(), id, request), "更新成功");
    }

    @DeleteMapping("/blocks/{id}")
    public Result<Void> deleteBlock(@PathVariable String id) {
        dailyPlanService.deleteBlock(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "删除成功");
    }

    // ============ 块状态机 ============

    /** 完成块（checkinHabit=true 且有 habitId 时联动完整打卡链路, 重复打卡幂等成功） */
    @PostMapping("/blocks/{id}/complete")
    public Result<PlanBlockResponse> complete(@PathVariable String id,
                                              @RequestBody(required = false) BlockCompleteRequest request) {
        boolean checkinHabit = request != null && Boolean.TRUE.equals(request.getCheckinHabit());
        return Result.success(dailyPlanService.completeBlock(SecurityUtils.getCurrentUserId(), id, checkinHabit), "已完成");
    }

    /** 跳过（PROPOSED/ADOPTED 可跳） */
    @PostMapping("/blocks/{id}/skip")
    public Result<PlanBlockResponse> skip(@PathVariable String id) {
        return Result.success(dailyPlanService.skipBlock(SecurityUtils.getCurrentUserId(), id), "已跳过");
    }

    /** 重开/撤销（DONE/SKIPPED→ADOPTED） */
    @PostMapping("/blocks/{id}/reopen")
    public Result<PlanBlockResponse> reopen(@PathVariable String id) {
        return Result.success(dailyPlanService.reopenBlock(SecurityUtils.getCurrentUserId(), id));
    }
}
