package com.habitforge.modules.environment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.habitforge.modules.environment.entity.EnvironmentSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EnvironmentSettingMapper extends BaseMapper<EnvironmentSetting> {
}
