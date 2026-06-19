package com.liang.data.agent.service.agentdatasource;

import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.service.agentdatasource.vo.AgentDatasourceColumnVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能体数据源字段权限服务。
 */
public interface AgentDatasourceColumnService {

    /**
     * 同步智能体选中表的字段元数据。
     */
    void syncColumns(Integer agentDatasourceId, DbConfigBO config, List<String> tableNames);

    /**
     * 查询当前智能体指定数据表的字段配置。
     */
    List<AgentDatasourceColumnVO> listColumns(Integer agentId, Integer datasourceId, String tableName);

    /**
     * 批量更新字段参与分析状态。
     */
    void updateAnalytic(Integer agentId, Integer datasourceId, String tableName,
                        List<String> columnNames, boolean isAnalytic);

    /**
     * 查询绑定下各表允许参与分析的字段。
     */
    Map<String, Set<String>> getAnalyticColumns(Integer agentDatasourceId, List<String> tableNames);
}
