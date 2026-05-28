package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.AgentDatasourceTablesEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 智能体数据源选中表 Mapper
 *
 * @author 资深Java架构师
 */
@Mapper
public interface AgentDatasourceTablesMapper extends BaseMapper<AgentDatasourceTablesEntity> {

    /**
     * 根据关联记录 ID 物理删除所有选中的表记录
     *
     * @param agentDatasourceId 智能体数据源关联记录ID
     * @return 影响行数
     */
    @Delete("""
            DELETE FROM agent_datasource_tables
            WHERE agent_datasource_id = #{agentDatasourceId}
            """)
    int deleteByAgentDatasourceId(@Param("agentDatasourceId") Integer agentDatasourceId);

    /**
     * 批量插入关联的数据表记录
     *
     * @param agentDatasourceId 智能体数据源关联记录ID
     * @param tables            选中的表名列表
     * @return 影响行数
     */
    @Insert("""
            <script>
                INSERT INTO agent_datasource_tables (agent_datasource_id, table_name) VALUES
                <foreach collection="tables" item="table" separator=",">
                   (#{agentDatasourceId}, #{table})
                </foreach>
            </script>
            """)
    int insertBatch(@Param("agentDatasourceId") Integer agentDatasourceId, @Param("tables") List<String> tables);

    /**
     * 查询关联记录下的所有已选中表名
     *
     * @param agentDatasourceId 智能体数据源关联记录ID
     * @return 表名列表
     */
    @Select("""
            SELECT table_name
            FROM agent_datasource_tables
            WHERE agent_datasource_id = #{agentDatasourceId}
            """)
    List<String> selectTablesByAgentDatasourceId(@Param("agentDatasourceId") Integer agentDatasourceId);
}
