package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体数据源关联表
 */
@Data
@TableName("agent_datasource")
public class AgentDatasourceEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 智能体ID */
    private Integer agentId;

    /** 数据源ID */
    private Integer datasourceId;

    /** 是否启用：0-禁用, 1-启用 */
    private Integer isActive;

    /** Schema 同步状态：pending / syncing / success / failed */
    private String schemaStatus;

    /** 向量化状态：pending / vectorizing / success / failed */
    private String embeddingStatus;

    /** 最后同步时间 */
    private LocalDateTime lastSyncTime;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
