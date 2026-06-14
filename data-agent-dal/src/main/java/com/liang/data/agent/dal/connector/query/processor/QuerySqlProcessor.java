package com.liang.data.agent.dal.connector.query.processor;

/**
 * 查询 SQL 处理器，用于执行查询语句提交数据库前的统一处理。
 */
@FunctionalInterface
public interface QuerySqlProcessor {

    /**
     * 处理待执行的查询 SQL。
     *
     * @param sql 原始查询 SQL
     * @return 处理后的查询 SQL
     */
    String prepare(String sql);
}
