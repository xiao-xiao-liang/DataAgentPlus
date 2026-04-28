package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能体表 Mapper
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}
