package com.liang.data.agent.dal.connector.bo;

/**
 * 外键信息
 *
 * @param sourceTable  主表名
 * @param sourceColumn 主表字段
 * @param targetTable  关联表名
 * @param targetColumn 关联表字段
 */
public record ForeignKeyInfoBO(
        String sourceTable,
        String sourceColumn,
        String targetTable,
        String targetColumn
) {
}