package com.habitforge.modules.contract.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.contract.dto.ContractRequest;
import com.habitforge.modules.contract.dto.ContractResponse;
import com.habitforge.modules.contract.dto.ContractUpdateRequest;
import com.habitforge.modules.contract.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 习惯契约（问责伙伴 + 违约代价）
 */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    public Result<List<ContractResponse>> list() {
        return Result.success(contractService.list(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping
    public Result<ContractResponse> create(@Valid @RequestBody ContractRequest request) {
        return Result.success(contractService.create(SecurityUtils.getCurrentUserId(), request), "契约已签订");
    }

    @PutMapping("/{id}")
    public Result<ContractResponse> update(@PathVariable String id,
                                           @Valid @RequestBody ContractUpdateRequest request) {
        return Result.success(contractService.update(SecurityUtils.getCurrentUserId(), id, request), "已保存");
    }

    /** 状态流转: ACTIVE / COMPLETED(做到) / BROKEN(破戒) */
    @PatchMapping("/{id}/status")
    public Result<ContractResponse> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        return Result.success(contractService.updateStatus(
                SecurityUtils.getCurrentUserId(), id, body.get("status")), "状态已更新");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        contractService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "已删除");
    }
}
