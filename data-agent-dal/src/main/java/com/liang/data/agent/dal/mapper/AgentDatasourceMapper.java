package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.AgentDatasourceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能体数据源关联表 Mapper
 */
@Mapper
public interface AgentDatasourceMapper extends BaseMapper<AgentDatasourceEntity> {
}
