package com.habitforge.modules.contract.service;

import com.habitforge.modules.contract.dto.ContractRequest;
import com.habitforge.modules.contract.dto.ContractResponse;
import com.habitforge.modules.contract.dto.ContractUpdateRequest;

import java.util.List;

/**
 * 习惯契约（问责伙伴 + 违约代价）
 */
public interface ContractService {

    /** 我的契约（按签订时间倒序） */
    List<ContractResponse> list(String userId);

    ContractResponse create(String userId, ContractRequest request);

    ContractResponse update(String userId, String id, ContractUpdateRequest request);

    ContractResponse updateStatus(String userId, String id, String status);

    void delete(String userId, String id);
}
