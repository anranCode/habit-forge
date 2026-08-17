package com.habitforge.modules.reflection.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.reflection.dto.ReflectionRequest;
import com.habitforge.modules.reflection.dto.ReflectionResponse;
import com.habitforge.modules.reflection.dto.ReflectionUpdateRequest;
import com.habitforge.modules.reflection.service.ReflectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reflections")
@RequiredArgsConstructor
public class ReflectionController {

    private final ReflectionService reflectionService;

    @PostMapping
    public Result<ReflectionResponse> create(@Valid @RequestBody ReflectionRequest request) {
        return Result.success(reflectionService.create(SecurityUtils.getCurrentUserId(), request), "心得记录成功");
    }

    @PutMapping("/{id}")
    public Result<ReflectionResponse> update(@PathVariable String id, @Valid @RequestBody ReflectionUpdateRequest request) {
        return Result.success(reflectionService.update(SecurityUtils.getCurrentUserId(), id, request), "更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        reflectionService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "删除成功");
    }

    /** 某日记下的全部心得 */
    @GetMapping("/journal/{journalId}")
    public Result<List<ReflectionResponse>> listByJournal(@PathVariable String journalId) {
        return Result.success(reflectionService.listByJournal(SecurityUtils.getCurrentUserId(), journalId));
    }

    /** 某习惯的全部心得（时间倒序） */
    @GetMapping("/habit/{habitId}")
    public Result<List<ReflectionResponse>> listByHabit(@PathVariable String habitId) {
        return Result.success(reflectionService.listByHabit(SecurityUtils.getCurrentUserId(), habitId));
    }
}
