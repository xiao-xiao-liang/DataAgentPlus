package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 某个智能体某个数据源所选中的数据表
 */
@Data
@TableName("agent_datasource_tables")
public class AgentDatasourceTablesEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 智能体数据源ID */
    private Integer agentDatasourceId;

    /** 数据表名 */
    private String tableName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
