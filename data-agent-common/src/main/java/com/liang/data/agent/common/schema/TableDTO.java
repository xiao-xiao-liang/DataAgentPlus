package com.liang.data.agent.common.schema;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 表描述 (表级别)
 */
@Data
@NoArgsConstructor
public class TableDTO {

    /**
     * 表名
     */
    private String name;

    /**
     * 表描述
     */
    private String description;

    /**
     * 列列表
     */
    private List<ColumnDTO> column = new ArrayList<>();

    /**
     * 主键列表
     */
    private List<String> primaryKeys;
}
