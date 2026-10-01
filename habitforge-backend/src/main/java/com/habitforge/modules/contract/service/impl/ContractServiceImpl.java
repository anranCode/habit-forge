package com.habitforge.modules.contract.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.contract.dto.ContractRequest;
import com.habitforge.modules.contract.dto.ContractResponse;
import com.habitforge.modules.contract.dto.ContractUpdateRequest;
import com.habitforge.modules.contract.entity.Contract;
import com.habitforge.modules.contract.mapper.ContractMapper;
import com.habitforge.modules.contract.service.ContractService;
import com.habitforge.modules.habit.dto.HabitResponseDTO;
import com.habitforge.modules.habit.service.HabitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractMapper contractMapper;
    private final HabitService habitService;

    @Override
    public List<ContractResponse> list(String userId) {
        List<Contract> contracts = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getUserId, userId)
                .orderByDesc(Contract::getSignedAt));
        // 契约上限 20, 且同一习惯可能有多条 —— 请求内缓存习惯名, 避免重复查
        Map<String, String> habitNames = new HashMap<>();
        return contracts.stream()
                .map(ct -> ContractResponse.of(ct, habitName(userId, ct.getHabitId(), habitNames)))
                .toList();
    }

    @Override
    public ContractResponse create(String userId, ContractRequest request) {
        Long count = contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getUserId, userId));
        if (count != null && count >= AppConstant.CONTRACT_MAX) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "契约最多 " + AppConstant.CONTRACT_MAX + " 份");
        }
        // 归属校验（非本人习惯抛 HABIT_NOT_FOUND）
        String name = habitService.getDetail(userId, request.getHabitId()).getName();

        Contract contract = new Contract();
        contract.setUserId(userId);
        contract.setHabitId(request.getHabitId());
        contract.setPartnerName(request.getPartnerName().strip());
        contract.setPenalty(request.getPenalty().strip());
        contract.setIsPublic(Boolean.TRUE.equals(request.getIsPublic()) ? 1 : 0);
        contract.setStatus(Contract.STATUS_ACTIVE);
        contractMapper.insert(contract);

        log.info("契约签订 user={}, habitId={}, partner={}", userId, request.getHabitId(), contract.getPartnerName());
        return ContractResponse.of(contract, name);
    }

    @Override
    public ContractResponse update(String userId, String id, ContractUpdateRequest request) {
        Contract contract = getOwned(userId, id);
        if (StrUtil.isNotBlank(request.getPartnerName())) {
            contract.setPartnerName(request.getPartnerName().strip());
        }
        if (StrUtil.isNotBlank(request.getPenalty())) {
            contract.setPenalty(request.getPenalty().strip());
        }
        if (request.getIsPublic() != null) {
            contract.setIsPublic(Boolean.TRUE.equals(request.getIsPublic()) ? 1 : 0);
        }
        if (StrUtil.isNotBlank(request.getStatus())) {
            contract.setStatus(request.getStatus());
        }
        contractMapper.updateById(contract);
        return ContractResponse.of(contract, habitName(userId, contract.getHabitId(), new HashMap<>()));
    }

    @Override
    public ContractResponse updateStatus(String userId, String id, String status) {
        if (!Contract.STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_INVALID);
        }
        Contract contract = getOwned(userId, id);
        contract.setStatus(status);
        contractMapper.updateById(contract);
        return ContractResponse.of(contract, habitName(userId, contract.getHabitId(), new HashMap<>()));
    }

    @Override
    public void delete(String userId, String id) {
        Contract contract = getOwned(userId, id);
        contractMapper.deleteById(contract.getId());
    }

    // ================= 内部 =================

    private Contract getOwned(String userId, String id) {
        Contract contract = contractMapper.selectById(id);
        if (contract == null || !contract.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CONTRACT_NOT_FOUND);
        }
        return contract;
    }

    /** 习惯被删/归档时契约仍在, 名字取不到就返回 null, 不让整个列表崩掉 */
    private String habitName(String userId, String habitId, Map<String, String> cache) {
        if (cache.containsKey(habitId)) {
            return cache.get(habitId);
        }
        String name = null;
        try {
            HabitResponseDTO habit = habitService.getDetail(userId, habitId);
            name = habit == null ? null : habit.getName();
        } catch (BusinessException e) {
            log.debug("契约关联的习惯已不可见 habitId={}: {}", habitId, e.getMessage());
        }
        cache.put(habitId, name);
        return name;
    }
}
