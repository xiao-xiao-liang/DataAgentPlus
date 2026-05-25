package com.liang.data.agent.common.schema;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 列描述 (列级别)
 */
@Data
@NoArgsConstructor
public class ColumnDTO {

    /**
     * 列名
     */
    private String name;

    /**
     * 列描述
     */
    private String description;

    /**
     * 是否枚举类型 (1=是)
     */
    private int enumeration;

    /**
     * 取值范围描述
     */
    private String range;

    /**
     * 数据类型 (如 VARCHAR, INT)
     */
    private String type;

    /**
     * 示例数据
     */
    private List<String> data;

    /**
     * 枚举值映射 (如 {"0": "否", "1": "是"})
     */
    private Map<String, String> mapping;
}