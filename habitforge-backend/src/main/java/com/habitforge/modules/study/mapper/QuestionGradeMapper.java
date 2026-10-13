package com.habitforge.modules.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.habitforge.modules.study.entity.QuestionGrade;
import org.apache.ibatis.annotations.Mapper;

/** 主观题判分留痕表 */
@Mapper
public interface QuestionGradeMapper extends BaseMapper<QuestionGrade> {
}
