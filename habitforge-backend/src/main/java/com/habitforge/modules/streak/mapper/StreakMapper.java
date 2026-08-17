package com.habitforge.modules.streak.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.habitforge.modules.streak.entity.Streak;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StreakMapper extends BaseMapper<Streak> {
}
