package com.habitforge.modules.environment.service;

import com.habitforge.modules.environment.dto.EnvironmentSettingRequest;
import com.habitforge.modules.environment.dto.EnvironmentSettingResponse;
import com.habitforge.modules.environment.dto.EnvironmentSettingUpdateRequest;

import java.util.List;

/**
 * 环境设计清单
 */
public interface EnvironmentSettingService {

    /** 我的清单（category 为 null 时返回全部, 按创建时间升序） */
    List<EnvironmentSettingResponse> list(String userId, String category);

    EnvironmentSettingResponse create(String userId, EnvironmentSettingRequest request);

    /** 批量添加（手机节制预设一键导入; 已存在同描述的项自动跳过, 幂等） */
    List<EnvironmentSettingResponse> createBatch(String userId, List<EnvironmentSettingRequest> requests);

    EnvironmentSettingResponse update(String userId, String id, EnvironmentSettingUpdateRequest request);

    /** 勾选/取消 */
    EnvironmentSettingResponse setActive(String userId, String id, boolean isActive);

    void delete(String userId, String id);
}
