package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.DatasourceTableColumnEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 智能体数据源表字段配置 Mapper。
 */
@Mapper
public interface DatasourceTableColumnMapper extends BaseMapper<DatasourceTableColumnEntity> {

    /**
     * 查询绑定下指定表的字段配置。
     */
    @Select("""
            SELECT *
            FROM datasource_table_columns
            WHERE agent_datasource_id = #{agentDatasourceId}
              AND table_name = #{tableName}
            ORDER BY id
            """)
    List<DatasourceTableColumnEntity> selectByBindingAndTable(@Param("agentDatasourceId") Integer agentDatasourceId,
                                                               @Param("tableName") String tableName);

    /**
     * 查询参与分析的字段名。
     */
    @Select("""
            SELECT column_name
            FROM datasource_table_columns
            WHERE agent_datasource_id = #{agentDatasourceId}
              AND table_name = #{tableName}
              AND is_analytic = 1
            """)
    List<String> selectAnalyticColumns(@Param("agentDatasourceId") Integer agentDatasourceId,
                                       @Param("tableName") String tableName);

    /**
     * 更新指定字段的参与分析状态。
     */
    @Update("""
            <script>
            UPDATE datasource_table_columns
            SET is_analytic = #{isAnalytic}, update_time = CURRENT_TIMESTAMP
            WHERE agent_datasource_id = #{agentDatasourceId}
              AND table_name = #{tableName}
              AND column_name IN
              <foreach collection="columnNames" item="columnName" open="(" separator="," close=")">
                #{columnName}
              </foreach>
            </script>
            """)
    int updateAnalytic(@Param("agentDatasourceId") Integer agentDatasourceId,
                       @Param("tableName") String tableName,
                       @Param("columnNames") List<String> columnNames,
                       @Param("isAnalytic") boolean isAnalytic);

    /**
     * 删除绑定下未出现在最新物理字段列表中的记录。
     */
    @Delete("""
            <script>
            DELETE FROM datasource_table_columns
            WHERE agent_datasource_id = #{agentDatasourceId}
              AND table_name = #{tableName}
              <if test="columnNames != null and columnNames.size() > 0">
                AND column_name NOT IN
                <foreach collection="columnNames" item="columnName" open="(" separator="," close=")">
                  #{columnName}
                </foreach>
              </if>
            </script>
            """)
    int deleteMissingColumns(@Param("agentDatasourceId") Integer agentDatasourceId,
                             @Param("tableName") String tableName,
                             @Param("columnNames") List<String> columnNames);

    /**
     * 删除绑定下的全部字段配置。
     */
    @Delete("DELETE FROM datasource_table_columns WHERE agent_datasource_id = #{agentDatasourceId}")
    int deleteByAgentDatasourceId(@Param("agentDatasourceId") Integer agentDatasourceId);

    /**
     * 删除绑定下未被选中的数据表字段配置。
     */
    @Delete("""
            <script>
            DELETE FROM datasource_table_columns
            WHERE agent_datasource_id = #{agentDatasourceId}
              <if test="tableNames != null and tableNames.size() > 0">
                AND table_name NOT IN
                <foreach collection="tableNames" item="tableName" open="(" separator="," close=")">
                  #{tableName}
                </foreach>
              </if>
            </script>
            """)
    int deleteUnselectedTables(@Param("agentDatasourceId") Integer agentDatasourceId,
                               @Param("tableNames") List<String> tableNames);
}
