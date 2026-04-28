package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.LogicalRelationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 逻辑外键配置表 Mapper
 */
@Mapper
public interface LogicalRelationMapper extends BaseMapper<LogicalRelationEntity> {
}
