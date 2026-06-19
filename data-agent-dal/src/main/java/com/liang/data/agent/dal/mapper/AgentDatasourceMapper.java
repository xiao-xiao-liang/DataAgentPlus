package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.AgentDatasourceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 智能体数据源关联表 Mapper
 */
@Mapper
public interface AgentDatasourceMapper extends BaseMapper<AgentDatasourceEntity> {

    @Select("SELECT datasource_id FROM agent_datasource WHERE agent_id = #{agentId} AND is_active = 1")
    Integer getActiveDatasource(@Param("agentId") Integer agentId);

    @Select("SELECT id FROM agent_datasource WHERE agent_id = #{agentId} AND is_active = 1")
    Integer getActiveBindingId(@Param("agentId") Integer agentId);

    /**
     * 根据智能体和数据源查询绑定 ID。
     */
    @Select("SELECT id FROM agent_datasource WHERE agent_id = #{agentId} AND datasource_id = #{datasourceId} AND del_flag = 0")
    Integer getBindingId(@Param("agentId") Integer agentId, @Param("datasourceId") Integer datasourceId);
}
