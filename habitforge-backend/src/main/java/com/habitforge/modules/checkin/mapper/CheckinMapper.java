package com.habitforge.modules.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.habitforge.modules.checkin.entity.Checkin;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CheckinMapper extends BaseMapper<Checkin> {
}
