package com.habitforge.modules.habit.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.habit.dto.*;
import com.habitforge.modules.habit.service.HabitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/habits")
@RequiredArgsConstructor
public class HabitController {

    private final HabitService habitService;

    @GetMapping
    public Result<List<HabitResponseDTO>> list(@RequestParam(required = false) Boolean activeOnly) {
        return Result.success(habitService.listMine(SecurityUtils.getCurrentUserId(), activeOnly));
    }

    /** 今日待打卡（按频率过滤） */
    @GetMapping("/today")
    public Result<List<HabitResponseDTO>> today() {
        return Result.success(habitService.listToday(SecurityUtils.getCurrentUserId()));
    }

    /** 追踪看板统计 */
    @GetMapping("/stats")
    public Result<HabitStatsDTO> stats() {
        return Result.success(habitService.getStats(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public Result<HabitResponseDTO> detail(@PathVariable String id) {
        return Result.success(habitService.getDetail(SecurityUtils.getCurrentUserId(), id));
    }

    @PostMapping
    public Result<HabitResponseDTO> create(@Valid @RequestBody HabitCreateDTO dto) {
        return Result.success(habitService.create(SecurityUtils.getCurrentUserId(), dto), "习惯创建成功");
    }

    @PutMapping("/{id}")
    public Result<HabitResponseDTO> update(@PathVariable String id, @Valid @RequestBody HabitUpdateDTO dto) {
        return Result.success(habitService.update(SecurityUtils.getCurrentUserId(), id, dto), "更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        habitService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "删除成功");
    }
}
