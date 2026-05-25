package com.liang.data.agent.common.schema;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据库 Schema 描述 (库级别)
 */
@Data
@NoArgsConstructor
public class SchemaDTO {

    /**
     * 数据库/Schema 名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 表数量
     */
    private Integer tableCount;

    /**
     * 表列表
     */
    private List<TableDTO> tables;

    /**
     * 外键关系描述
     */
    private List<String> foreignKeys;
}
