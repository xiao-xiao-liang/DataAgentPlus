package com.liang.data.agent.service.agentdatasource.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.liang.data.agent.ai.schema.SchemaService;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.entity.AgentDatasourceEntity;
import com.liang.data.agent.dal.entity.DatasourceTableColumnEntity;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import com.liang.data.agent.dal.mapper.AgentDatasourceMapper;
import com.liang.data.agent.dal.mapper.AgentDatasourceTablesMapper;
import com.liang.data.agent.dal.mapper.DatasourceTableColumnMapper;
import com.liang.data.agent.dal.mapper.DatasourceMapper;
import com.liang.data.agent.service.agentdatasource.AgentDatasourceColumnService;
import com.liang.data.agent.service.agentdatasource.vo.AgentDatasourceColumnVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能体数据源字段权限服务实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentDatasourceColumnServiceImpl implements AgentDatasourceColumnService {

    private final DatabaseAccessor databaseAccessor;
    private final AgentDatasourceMapper agentDatasourceMapper;
    private final AgentDatasourceTablesMapper agentDatasourceTablesMapper;
    private final DatasourceTableColumnMapper columnMapper;
    private final DatasourceMapper datasourceMapper;
    private final SchemaService schemaService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncColumns(Integer agentDatasourceId, DbConfigBO config, List<String> tableNames) {
        // 1. 清理当前智能体已取消选择的数据表字段配置。
        columnMapper.deleteUnselectedTables(agentDatasourceId, tableNames);

        // 2. 按表读取最新物理字段。
        for (String tableName : tableNames) {
            List<ColumnInfoBO> physicalColumns = databaseAccessor.showColumns(config, tableName);
            List<String> physicalNames = physicalColumns.stream().map(ColumnInfoBO::columnName).toList();
            Map<String, DatasourceTableColumnEntity> existing = columnMapper
                    .selectByBindingAndTable(agentDatasourceId, tableName).stream()
                    .collect(java.util.stream.Collectors.toMap(DatasourceTableColumnEntity::getColumnName, item -> item));

            // 3. 更新已有字段元数据，并为新增字段创建默认参与分析配置。
            for (ColumnInfoBO column : physicalColumns) {
                DatasourceTableColumnEntity entity = existing.getOrDefault(column.columnName(), new DatasourceTableColumnEntity());
                entity.setAgentDatasourceId(agentDatasourceId);
                entity.setTableName(tableName);
                entity.setColumnName(column.columnName());
                entity.setDataType(column.dataType());
                entity.setColumnComment(column.comment());
                entity.setIsNullable(column.nullable());
                entity.setIsPrimaryKey(column.primaryKey());
                if (entity.getIsAnalytic() == null) {
                    entity.setIsAnalytic(true);
                }
                if (entity.getId() == null) {
                    columnMapper.insert(entity);
                } else {
                    columnMapper.updateById(entity);
                }
            }

            // 4. 移除物理数据库中已经不存在的字段。
            columnMapper.deleteMissingColumns(agentDatasourceId, tableName, physicalNames);
        }
    }

    @Override
    public List<AgentDatasourceColumnVO> listColumns(Integer agentId, Integer datasourceId, String tableName) {
        Integer bindingId = requireSelectedTable(agentId, datasourceId, tableName);
        return columnMapper.selectByBindingAndTable(bindingId, tableName).stream().map(this::toVO).toList();
    }

    @Override
    public void updateAnalytic(Integer agentId, Integer datasourceId, String tableName,
                               List<String> columnNames, boolean isAnalytic) {
        Integer bindingId = requireSelectedTable(agentId, datasourceId, tableName);
        if (columnNames == null || columnNames.isEmpty()) {
            throw new ServiceException("字段列表不能为空");
        }
        columnMapper.updateAnalytic(bindingId, tableName, columnNames, isAnalytic);

        // 1. 字段权限先持久化，确保 SQL 权限立即生效。
        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        List<String> selectedTables = agentDatasourceTablesMapper.selectTablesByAgentDatasourceId(bindingId);

        // 2. 重新生成当前智能体 Schema 向量，避免旧字段继续被召回。
        try {
            schemaService.initializeSchema(agentId, datasourceId, DbConfigBO.from(datasource), selectedTables);
        } catch (RuntimeException e) {
            log.error("字段权限已保存，但向量同步失败，agentId={}, datasourceId={}, tableName={}",
                    agentId, datasourceId, tableName, e);
            throw new ServiceException("字段权限已保存，向量同步失败，可重试");
        }
    }

    @Override
    public Map<String, Set<String>> getAnalyticColumns(Integer agentDatasourceId, List<String> tableNames) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            result.put(tableName, new LinkedHashSet<>(columnMapper.selectAnalyticColumns(agentDatasourceId, tableName)));
        }
        return result;
    }

    private Integer requireSelectedTable(Integer agentId, Integer datasourceId, String tableName) {
        AgentDatasourceEntity binding = agentDatasourceMapper.selectOne(Wrappers.<AgentDatasourceEntity>lambdaQuery()
                .eq(AgentDatasourceEntity::getAgentId, agentId)
                .eq(AgentDatasourceEntity::getDatasourceId, datasourceId));
        if (binding == null) {
            throw new ServiceException("当前智能体未绑定该数据源");
        }
        if (!agentDatasourceTablesMapper.selectTablesByAgentDatasourceId(binding.getId()).contains(tableName)) {
            throw new ServiceException("当前智能体未选择该数据表");
        }
        return binding.getId();
    }

    private AgentDatasourceColumnVO toVO(DatasourceTableColumnEntity entity) {
        AgentDatasourceColumnVO vo = new AgentDatasourceColumnVO();
        vo.setColumnName(entity.getColumnName());
        vo.setDataType(entity.getDataType());
        vo.setComment(entity.getColumnComment());
        vo.setNullable(entity.getIsNullable());
        vo.setPrimaryKey(entity.getIsPrimaryKey());
        vo.setIsAnalytic(entity.getIsAnalytic());
        return vo;
    }
}
