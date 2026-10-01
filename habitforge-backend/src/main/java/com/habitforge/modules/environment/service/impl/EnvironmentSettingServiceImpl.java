package com.habitforge.modules.environment.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.environment.dto.EnvironmentSettingRequest;
import com.habitforge.modules.environment.dto.EnvironmentSettingResponse;
import com.habitforge.modules.environment.dto.EnvironmentSettingUpdateRequest;
import com.habitforge.modules.environment.entity.EnvironmentSetting;
import com.habitforge.modules.environment.mapper.EnvironmentSettingMapper;
import com.habitforge.modules.environment.service.EnvironmentSettingService;
import com.habitforge.modules.habit.service.HabitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentSettingServiceImpl implements EnvironmentSettingService {

    private final EnvironmentSettingMapper environmentSettingMapper;
    private final HabitService habitService;

    @Override
    public List<EnvironmentSettingResponse> list(String userId, String category) {
        LambdaQueryWrapper<EnvironmentSetting> wrapper = new LambdaQueryWrapper<EnvironmentSetting>()
                .eq(EnvironmentSetting::getUserId, userId);
        if (StrUtil.isNotBlank(category)) {
            wrapper.eq(EnvironmentSetting::getCategory, category);
        }
        return environmentSettingMapper.selectList(wrapper.orderByAsc(EnvironmentSetting::getCreatedAt))
                .stream().map(EnvironmentSettingResponse::from).toList();
    }

    @Override
    public EnvironmentSettingResponse create(String userId, EnvironmentSettingRequest request) {
        ensureCapacity(userId, 1);
        EnvironmentSetting setting = build(userId, request);
        environmentSettingMapper.insert(setting);
        return EnvironmentSettingResponse.from(setting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EnvironmentSettingResponse> createBatch(String userId, List<EnvironmentSettingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供要添加的清单项");
        }
        // 必须显式收成可变集合: Stream.toList() 返回不可变列表, 下面还要往里追加已处理过的描述
        List<String> existing = environmentSettingMapper.selectList(new LambdaQueryWrapper<EnvironmentSetting>()
                        .eq(EnvironmentSetting::getUserId, userId)
                        .select(EnvironmentSetting::getDescription))
                .stream().map(EnvironmentSetting::getDescription)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        List<EnvironmentSetting> created = new ArrayList<>();
        for (EnvironmentSettingRequest req : requests) {
            String desc = req.getDescription() == null ? "" : req.getDescription().strip();
            if (desc.isEmpty() || existing.contains(desc)) {
                continue; // 空描述与重复项直接跳过, 让"一键导入"可重复点
            }
            ensureCapacity(userId, created.size() + 1);
            EnvironmentSetting setting = build(userId, req);
            environmentSettingMapper.insert(setting);
            created.add(setting);
            existing.add(desc);
        }
        log.info("环境清单批量导入 user={}, 提交 {} 项, 实际新增 {} 项", userId, requests.size(), created.size());
        return created.stream().map(EnvironmentSettingResponse::from).toList();
    }

    @Override
    public EnvironmentSettingResponse update(String userId, String id, EnvironmentSettingUpdateRequest request) {
        EnvironmentSetting setting = getOwned(userId, id);
        if (StrUtil.isNotBlank(request.getType())) {
            setting.setType(request.getType());
        }
        if (StrUtil.isNotBlank(request.getCategory())) {
            setting.setCategory(request.getCategory());
        }
        if (request.getDescription() != null) {
            if (request.getDescription().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "设置内容不能为空");
            }
            setting.setDescription(request.getDescription().strip());
        }
        if (request.getTargetHabitId() != null) {
            // 空串 = 解除关联
            setting.setTargetHabitId(request.getTargetHabitId().isBlank() ? null : request.getTargetHabitId());
            validateHabit(userId, setting.getTargetHabitId());
        }
        if (request.getIsActive() != null) {
            setting.setIsActive(Boolean.TRUE.equals(request.getIsActive()) ? 1 : 0);
        }
        environmentSettingMapper.updateById(setting);
        return EnvironmentSettingResponse.from(setting);
    }

    @Override
    public EnvironmentSettingResponse setActive(String userId, String id, boolean isActive) {
        EnvironmentSetting setting = getOwned(userId, id);
        setting.setIsActive(isActive ? 1 : 0);
        environmentSettingMapper.updateById(setting);
        return EnvironmentSettingResponse.from(setting);
    }

    @Override
    public void delete(String userId, String id) {
        EnvironmentSetting setting = getOwned(userId, id);
        environmentSettingMapper.deleteById(setting.getId());
    }

    // ================= 内部 =================

    private EnvironmentSetting build(String userId, EnvironmentSettingRequest request) {
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写设置内容");
        }
        String type = StrUtil.isBlank(request.getType()) ? EnvironmentSetting.TYPE_RESISTANCE : request.getType();
        if (!EnvironmentSetting.TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "类型取值不合法(PROMPT/RESISTANCE/COMMITMENT)");
        }
        String category = StrUtil.isBlank(request.getCategory())
                ? EnvironmentSetting.CATEGORY_OTHER : request.getCategory();
        if (!EnvironmentSetting.CATEGORIES.contains(category)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "类别取值不合法(PHONE/HABIT/OTHER)");
        }
        validateHabit(userId, request.getTargetHabitId());

        EnvironmentSetting setting = new EnvironmentSetting();
        setting.setUserId(userId);
        setting.setType(type);
        setting.setCategory(category);
        setting.setDescription(request.getDescription().strip());
        setting.setTargetHabitId(StrUtil.isBlank(request.getTargetHabitId()) ? null : request.getTargetHabitId());
        // 默认直接生效：清单项加进来就是为了去做, 不需要再点一次
        setting.setIsActive(request.getIsActive() == null || Boolean.TRUE.equals(request.getIsActive()) ? 1 : 0);
        return setting;
    }

    private void validateHabit(String userId, String habitId) {
        if (StrUtil.isNotBlank(habitId)) {
            habitService.getDetail(userId, habitId); // 非本人习惯会抛 HABIT_NOT_FOUND
        }
    }

    private void ensureCapacity(String userId, int adding) {
        Long count = environmentSettingMapper.selectCount(new LambdaQueryWrapper<EnvironmentSetting>()
                .eq(EnvironmentSetting::getUserId, userId));
        long current = count == null ? 0 : count;
        if (current + adding > AppConstant.ENV_SETTING_MAX) {
            throw new BusinessException(ErrorCode.ENV_SETTING_LIMIT_EXCEEDED,
                    "环境清单最多 " + AppConstant.ENV_SETTING_MAX + " 条");
        }
    }

    private EnvironmentSetting getOwned(String userId, String id) {
        EnvironmentSetting setting = environmentSettingMapper.selectById(id);
        if (setting == null || !setting.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ENV_SETTING_NOT_FOUND);
        }
        return setting;
    }
}
