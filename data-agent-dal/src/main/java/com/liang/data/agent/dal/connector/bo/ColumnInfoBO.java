package com.liang.data.agent.dal.connector.bo;

/**
 * 字段信息
 *
 * @param columnName 字段名
 * @param dataType   数据类型
 * @param comment    字段注释
 * @param nullable   是否允许 NULL
 * @param primaryKey 是否主键
 */
public record ColumnInfoBO(
        String columnName,
        String dataType,
        String comment,
        boolean nullable,
        boolean primaryKey
) {
}