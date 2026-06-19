package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体数据源表字段配置实体。
 */
@Data
@TableName("datasource_table_columns")
public class DatasourceTableColumnEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 智能体数据源关联 ID */
    private Integer agentDatasourceId;

    /** 数据表名 */
    private String tableName;

    /** 字段名 */
    private String columnName;

    /** 数据类型 */
    private String dataType;

    /** 字段注释 */
    private String columnComment;

    /** 是否允许为空 */
    private Boolean isNullable;

    /** 是否为主键 */
    private Boolean isPrimaryKey;

    /** 是否参与分析 */
    private Boolean isAnalytic;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
