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

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
