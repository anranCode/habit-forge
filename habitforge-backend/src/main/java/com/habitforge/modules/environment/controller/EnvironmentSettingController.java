package com.habitforge.modules.environment.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.environment.dto.EnvironmentSettingActiveRequest;
import com.habitforge.modules.environment.dto.EnvironmentSettingRequest;
import com.habitforge.modules.environment.dto.EnvironmentSettingResponse;
import com.habitforge.modules.environment.dto.EnvironmentSettingUpdateRequest;
import com.habitforge.modules.environment.service.EnvironmentSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 环境设计清单（手机节制 / 习惯环境）
 */
@RestController
@RequestMapping("/api/v1/environment-settings")
@RequiredArgsConstructor
public class EnvironmentSettingController {

    private final EnvironmentSettingService environmentSettingService;

    /** 我的清单（可选 category=PHONE/HABIT/OTHER 过滤） */
    @GetMapping
    public Result<List<EnvironmentSettingResponse>> list(@RequestParam(required = false) String category) {
        return Result.success(environmentSettingService.list(SecurityUtils.getCurrentUserId(), category));
    }

    @PostMapping
    public Result<EnvironmentSettingResponse> create(@Valid @RequestBody EnvironmentSettingRequest request) {
        return Result.success(environmentSettingService.create(SecurityUtils.getCurrentUserId(), request), "已添加");
    }

    /** 批量添加（预设清单一键导入; 重复描述自动跳过, 可重复点击） */
    @PostMapping("/batch")
    public Result<List<EnvironmentSettingResponse>> createBatch(
            @Valid @RequestBody List<EnvironmentSettingRequest> requests) {
        List<EnvironmentSettingResponse> created =
                environmentSettingService.createBatch(SecurityUtils.getCurrentUserId(), requests);
        return Result.success(created, created.isEmpty() ? "这些项都已经在清单里了" : "已添加 " + created.size() + " 项");
    }

    @PutMapping("/{id}")
    public Result<EnvironmentSettingResponse> update(@PathVariable String id,
                                                     @Valid @RequestBody EnvironmentSettingUpdateRequest request) {
        return Result.success(environmentSettingService.update(SecurityUtils.getCurrentUserId(), id, request), "已保存");
    }

    /** 勾选/取消 */
    @PatchMapping("/{id}/active")
    public Result<EnvironmentSettingResponse> setActive(@PathVariable String id,
                                                        @Valid @RequestBody EnvironmentSettingActiveRequest request) {
        return Result.success(environmentSettingService.setActive(
                SecurityUtils.getCurrentUserId(), id, Boolean.TRUE.equals(request.getIsActive())));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        environmentSettingService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "已删除");
    }
}
