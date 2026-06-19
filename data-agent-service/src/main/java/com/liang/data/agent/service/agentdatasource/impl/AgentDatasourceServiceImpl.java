package com.liang.data.agent.service.agentdatasource.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.liang.data.agent.ai.schema.SchemaService;
import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.common.constant.VectorMetadataKey;
import com.liang.data.agent.common.enums.VectorType;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.common.util.BeanUtil;
import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;
import com.liang.data.agent.dal.entity.AgentDatasourceEntity;
import com.liang.data.agent.dal.entity.AgentEntity;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import com.liang.data.agent.dal.mapper.AgentDatasourceMapper;
import com.liang.data.agent.dal.mapper.AgentDatasourceTablesMapper;
import com.liang.data.agent.dal.mapper.DatasourceTableColumnMapper;
import com.liang.data.agent.service.agentdatasource.AgentDatasourceColumnService;
import com.liang.data.agent.service.agentdatasource.AgentDatasourceService;
import com.liang.data.agent.service.agentdatasource.vo.AgentDatasourceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.liang.data.agent.common.constant.VectorMetadataKey.AGENT_ID;
import static com.liang.data.agent.common.constant.VectorMetadataKey.VECTOR_TYPE;

/**
 * 智能体-数据源关联服务实现
 * 结合 MP 链式编程与 AgentDatasourceTablesMapper 的手写注解 SQL 实现，保证性能与结构清晰
 *
 * @author 资深Java架构师
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDatasourceServiceImpl extends ServiceImpl<AgentDatasourceMapper, AgentDatasourceEntity> implements AgentDatasourceService {

    private final SchemaService schemaService;
    private final DatabaseAccessor databaseAccessor;
    private final AgentVectorStoreService agentVectorStoreService;
    private final AgentDatasourceTablesMapper agentDatasourceTablesMapper;
    private final DatasourceTableColumnMapper datasourceTableColumnMapper;
    private final AgentDatasourceColumnService agentDatasourceColumnService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindDatasource(Integer agentId, Integer datasourceId, List<String> tables) {
        // 1. 校验 Agent 和 Datasource 存在 (使用 Db 静态链式查询)
        Db.lambdaQuery(AgentEntity.class)
                .eq(AgentEntity::getId, agentId)
                .oneOpt()
                .orElseThrow(() -> new ServiceException("智能体不存在, id=" + agentId));

        Db.lambdaQuery(DatasourceEntity.class)
                .eq(DatasourceEntity::getId, datasourceId)
                .oneOpt()
                .orElseThrow(() -> new ServiceException("数据源不存在, id=" + datasourceId));

        // 2. 查询当前绑定 (使用本类 lambda 链式)
        AgentDatasourceEntity existing = findByAgentId(agentId);

        if (existing != null) {
            // 已绑定相同数据源
            if (existing.getDatasourceId().equals(datasourceId)) {
                log.info("智能体已绑定该数据源, 重新同步选中的数据表, agentId={}, datasourceId={}, tables={}", agentId, datasourceId, tables);
                
                // 重置选中的数据表 (使用自定义的注解删除方法)
                agentDatasourceTablesMapper.deleteByAgentDatasourceId(existing.getId());
                
                // 批量插入选中的表 (使用自定义的注解批量插入方法)
                if (CollectionUtils.isNotEmpty(tables)) {
                    agentDatasourceTablesMapper.insertBatch(existing.getId(), tables);
                }
                
                // 异步触发 Schema 强同步
                CompletableFuture.runAsync(() -> {
                    try {
                        syncSchema(agentId, tables);
                    } catch (Exception e) {
                        log.error("异步 Schema 同步失败, agentId={}", agentId, e);
                    }
                });
                return;
            }
            
            // 已绑定不同数据源，先解绑旧的
            log.info("智能体已绑定其他数据源, 先解绑旧的, agentId={}, oldDatasourceId={}", agentId, existing.getDatasourceId());
            unbindDatasource(agentId);
        }

        // 3. 创建新的关联记录 (使用继承自 ServiceImpl 的 save 方法)
        AgentDatasourceEntity entity = new AgentDatasourceEntity();
        entity.setAgentId(agentId);
        entity.setDatasourceId(datasourceId);
        entity.setSchemaStatus("pending");
        entity.setEmbeddingStatus("pending");
        this.save(entity);

        // 4. 批量插入勾选的数据表 (使用自定义的注解批量插入方法)
        if (tables != null && !tables.isEmpty()) {
            agentDatasourceTablesMapper.insertBatch(entity.getId(), tables);
        }

        log.info("绑定数据源成功, agentId={}, datasourceId={}, tables={}", agentId, datasourceId, tables);

        // 5. 异步触发 Schema 同步
        CompletableFuture.runAsync(() -> {
            try {
                syncSchema(agentId, tables);
            } catch (Exception e) {
                log.error("异步 Schema 同步失败, agentId={}", agentId, e);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindDatasource(Integer agentId) {
        AgentDatasourceEntity existing = findByAgentId(agentId);
        if (existing == null) {
            log.info("智能体无绑定数据源, agentId={}", agentId);
            return;
        }

        // 1. 逻辑删除关联记录
        removeById(existing.getId());

        // 2. 物理清理选中的表关联记录
        agentDatasourceTablesMapper.deleteByAgentDatasourceId(existing.getId());

        // 3. 物理清理字段配置
        datasourceTableColumnMapper.deleteByAgentDatasourceId(existing.getId());

        // 4. 清除向量文档
        try {
            agentVectorStoreService.deleteDocumentsByMetadata(Map.of(
                    AGENT_ID, String.valueOf(agentId),
                    VECTOR_TYPE, VectorType.TABLE.getCode()
            ));
            agentVectorStoreService.deleteDocumentsByMetadata(Map.of(
                    AGENT_ID, String.valueOf(agentId),
                    VECTOR_TYPE, VectorType.COLUMN.getCode()
            ));
            log.info("解绑数据源并清除向量文档成功, agentId={}, datasourceId={}",
                    agentId, existing.getDatasourceId());
        } catch (Exception e) {
            log.error("清除向量文档失败, agentId={}, 但关联已解除", agentId, e);
        }
    }

    @Override
    public AgentDatasourceVO getCurrentBinding(Integer agentId) {
        AgentDatasourceEntity entity = findByAgentId(agentId);
        if (entity == null) {
            return null;
        }
        return toVO(entity);
    }

    @Override
    public void syncSchema(Integer agentId, List<String> tables) {
        // 1. 查询当前绑定
        AgentDatasourceEntity binding = Optional.ofNullable(findByAgentId(agentId))
                .orElseThrow(() -> new ServiceException("智能体未绑定数据源, 无法同步 Schema, agentId=" + agentId));

        Integer datasourceId = binding.getDatasourceId();
        DatasourceEntity datasource = Db.lambdaQuery(DatasourceEntity.class)
                .eq(DatasourceEntity::getId, datasourceId)
                .oneOpt()
                .orElseThrow(() -> new ServiceException("关联的数据源不存在, datasourceId=" + datasourceId));

        DbConfigBO config = DbConfigBO.from(datasource);
        log.info("开始 Schema 同步, agentId={}, datasourceId={}, database={}",
                agentId, datasource.getId(), datasource.getDatabaseName());

        // 2. 更新状态为 syncing (使用 lambdaUpdate 链式更新)
        this.lambdaUpdate()
                .eq(AgentDatasourceEntity::getId, binding.getId())
                .set(AgentDatasourceEntity::getSchemaStatus, "syncing")
                .set(AgentDatasourceEntity::getEmbeddingStatus, "vectorizing")
                .update();
        
        // 同步更新 binding 内存对象属性
        binding.setSchemaStatus("syncing");
        binding.setEmbeddingStatus("vectorizing");

        try {
            List<String> targetTables = tables;
            
            // 3. 如果入参表列表为空，尝试从已关联的表记录中查找 (使用自定义的注解查询方法)
            if (targetTables == null || targetTables.isEmpty()) {
                targetTables = agentDatasourceTablesMapper.selectTablesByAgentDatasourceId(binding.getId());
            }
            
            // 4. 如果依然为空，则降级同步该数据源下的所有表名
            if (targetTables.isEmpty()) {
                List<TableInfoBO> allTables = databaseAccessor.showTables(config, null);
                targetTables = allTables.stream().map(TableInfoBO::tableName).toList();
            }

            if (targetTables.isEmpty()) {
                log.warn("数据源下无可同步的表, 清理旧向量数据并跳过向量化, agentId={}", agentId);
                agentVectorStoreService.deleteDocumentsByMetadata(Map.of(
                        AGENT_ID, String.valueOf(agentId),
                        VECTOR_TYPE, VectorType.TABLE.getCode()
                ));
                agentVectorStoreService.deleteDocumentsByMetadata(Map.of(
                        AGENT_ID, String.valueOf(agentId),
                        VECTOR_TYPE, VectorType.COLUMN.getCode()
                ));
                updateSyncStatus(binding, "success", "success");
                return;
            }

            // 5. 同步当前智能体选中表的字段元数据与分析状态
            agentDatasourceColumnService.syncColumns(binding.getId(), config, targetTables);

            // 6. 调用 SchemaService 进行完整的 Schema 初始化与向量化写入
            schemaService.initializeSchema(agentId, datasource.getId(), config, targetTables);

            // 7. 更新状态为成功
            updateSyncStatus(binding, "success", "success");
            log.info("Schema 同步及向量化完成, agentId={}, tables={}", agentId, targetTables.size());

        } catch (Exception e) {
            log.error("Schema 同步失败, agentId={}", agentId, e);
            updateSyncStatus(binding, "failed", "failed");
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 根据 agentId 查询当前唯一绑定
     */
    private AgentDatasourceEntity findByAgentId(Integer agentId) {
        return this.lambdaQuery()
                .eq(AgentDatasourceEntity::getAgentId, agentId)
                .oneOpt()
                .orElse(null);
    }

    /**
     * 更新同步状态 (使用 lambdaUpdate 链式更新)
     */
    private void updateSyncStatus(AgentDatasourceEntity binding, String schemaStatus, String embeddingStatus) {
        this.lambdaUpdate()
                .eq(AgentDatasourceEntity::getId, binding.getId())
                .set(AgentDatasourceEntity::getSchemaStatus, schemaStatus)
                .set(AgentDatasourceEntity::getEmbeddingStatus, embeddingStatus)
                .set(AgentDatasourceEntity::getLastSyncTime, LocalDateTime.now())
                .update();
        
        binding.setSchemaStatus(schemaStatus);
        binding.setEmbeddingStatus(embeddingStatus);
        binding.setLastSyncTime(LocalDateTime.now());
    }

    /**
     * 实体转视图对象
     */
    private AgentDatasourceVO toVO(AgentDatasourceEntity entity) {
        AgentDatasourceVO vo = BeanUtil.copyProperties(entity, AgentDatasourceVO.class);

        // 关联查询数据源名称和类型 (使用 Db 静态链式查询)
        DatasourceEntity datasource = Db.lambdaQuery(DatasourceEntity.class)
                .eq(DatasourceEntity::getId, entity.getDatasourceId())
                .oneOpt()
                .orElse(null);
        if (datasource != null) {
            vo.setDatasourceName(datasource.getName());
            vo.setDatasourceType(datasource.getType());
        }

        // 查询已绑定的表列表 (使用自定义的注解查询方法直接返回 List<String>)
        List<String> selectTables = agentDatasourceTablesMapper.selectTablesByAgentDatasourceId(entity.getId());
        if (selectTables != null && !selectTables.isEmpty()) {
            vo.setSelectTables(selectTables);
        }
        return vo;
    }
}
