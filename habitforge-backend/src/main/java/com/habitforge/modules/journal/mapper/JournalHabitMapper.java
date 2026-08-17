package com.habitforge.modules.journal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.habitforge.modules.journal.entity.JournalHabit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JournalHabitMapper extends BaseMapper<JournalHabit> {
}
