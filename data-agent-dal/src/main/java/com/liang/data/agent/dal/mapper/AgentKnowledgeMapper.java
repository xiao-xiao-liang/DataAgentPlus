package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能体知识源管理表 Mapper
 */
@Mapper
public interface AgentKnowledgeMapper extends BaseMapper<AgentKnowledgeEntity> {
}
