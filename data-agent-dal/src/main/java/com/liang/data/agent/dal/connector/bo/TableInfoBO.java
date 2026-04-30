package com.liang.data.agent.dal.connector.bo;

/**
 * 表信息
 *
 * @param tableName 表名
 * @param comment   表注释
 */
public record TableInfoBO(String tableName, String comment) {
}