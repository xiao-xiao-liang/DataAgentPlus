package com.liang.data.agent.dal.connector.ddl;

import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.ForeignKeyInfoBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * DDL 执行器
 */
public interface DdlExecutor {

    /**
     * 查询数据库中的所有表
     */
    List<TableInfoBO> showTables(Connection conn, String schema, String pattern) throws SQLException;

    /**
     * 查询某张表所有字段信息
     */
    List<ColumnInfoBO> showColumns(Connection conn, String schema, String table) throws SQLException;

    /**
     * 查询多张表的外键关系 (物理外键)
     */
    List<ForeignKeyInfoBO> showForeignKeys(Connection conn, String schema, List<String> tables) throws SQLException;

    /**
     * 采样某个字段的值 (帮助 LLM 理解字段内容)
     */
    List<String> sampleColumn(Connection conn, String schema, String table, String column) throws SQLException;

    /**
     * 是否支持该数据库类型
     */
    boolean supports(String type);
}
