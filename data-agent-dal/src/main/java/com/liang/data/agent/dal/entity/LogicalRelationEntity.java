package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逻辑外键配置表
 */
@Data
@TableName("logical_relation")
public class LogicalRelationEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 关联的数据源ID */
    private Integer datasourceId;

    /** 主表名 */
    private String sourceTableName;

    /** 主表字段名 */
    private String sourceColumnName;

    /** 关联表名 */
    private String targetTableName;

    /** 关联表字段名 */
    private String targetColumnName;

    /** 关系类型: 1:1, 1:N, N:1 */
    private String relationType;

    /** 业务描述: 帮助 LLM 理解关联关系 */
    private String description;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
