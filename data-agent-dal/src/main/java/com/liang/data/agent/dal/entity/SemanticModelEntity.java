package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语义模型表
 */
@Data
@TableName("semantic_model")
public class SemanticModelEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 关联的智能体ID */
    private Integer agentId;

    /** 关联的数据源ID */
    private Integer datasourceId;

    /** 关联的表名 */
    private String tableName;

    /** 物理字段名 (例如: csat_score) */
    private String columnName;

    /** 业务名/别名 (例如: 客户满意度分数) */
    private String businessName;

    /** 同义词 */
    private String synonyms;

    /** 业务描述 (帮助 LLM 理解字段含义) */
    private String businessDescription;

    /** 物理字段的原始注释 */
    private String columnComment;

    /** 物理数据类型 (例如: int, varchar(20)) */
    private String dataType;

    /** 0=停用, 1=启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
