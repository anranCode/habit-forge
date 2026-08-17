package com.habitforge.modules.reflection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.habitforge.modules.reflection.entity.HabitReflection;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HabitReflectionMapper extends BaseMapper<HabitReflection> {
}
