package com.habitforge.modules.habit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.habitforge.modules.habit.entity.Habit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HabitMapper extends BaseMapper<Habit> {
}
