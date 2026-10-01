package com.habitforge.modules.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.habitforge.modules.contract.entity.Contract;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContractMapper extends BaseMapper<Contract> {
}
