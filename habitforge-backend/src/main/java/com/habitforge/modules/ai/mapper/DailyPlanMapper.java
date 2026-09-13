package com.habitforge.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.habitforge.modules.ai.entity.DailyPlan;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DailyPlanMapper extends BaseMapper<DailyPlan> {
}
