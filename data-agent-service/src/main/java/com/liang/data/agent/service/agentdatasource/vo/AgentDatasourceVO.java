package com.liang.data.agent.service.agentdatasource.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 智能体-数据源关联视图对象
 * <p>
 * 包含数据源的名称和类型等冗余信息，方便前端直接展示。
 * </p>
 */
@Data
@Accessors(chain = true)
public class AgentDatasourceVO {

    /**
     * 关联记录ID
     */
    private Integer id;

    /**
     * 智能体ID
     */
    private Integer agentId;

    /**
     * 数据源ID
     */
    private Integer datasourceId;

    /**
     * 数据源名称（关联查询）
     */
    private String datasourceName;

    /**
     * 数据源类型（关联查询）
     */
    private String datasourceType;

    /**
     * Schema 同步状态: pending / syncing / success / failed
     */
    private String schemaStatus;

    /**
     * 向量化状态: pending / vectorizing / success / failed
     */
    private String embeddingStatus;

    /**
     * 最近一次同步时间
     */
    private LocalDateTime lastSyncTime;

    /**
     * 选中的数据表列表
     */
    private java.util.List<String> selectTables;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
