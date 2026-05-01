package com.liang.data.agent.dal.connector.dialect;

import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.bo.ForeignKeyInfoBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据库方言接口
 * 整合了数据源连接属性配置与 DDL 元数据查询能力
 */
public interface DatabaseDialect {

    /** 数据库类型标识，如 "mysql" */
    String type();

    /** JDBC 驱动类名 */
    String driver();

    /** 根据配置构建完整 JDBC URL */
    String buildJdbcUrl(DbConfigBO config);

    /** 连接验证 SQL */
    String validationQuery();

    List<TableInfoBO> showTables(Connection conn, String schema, String pattern) throws SQLException;

    List<ColumnInfoBO> showColumns(Connection conn, String schema, String table) throws SQLException;

    List<ForeignKeyInfoBO> showForeignKeys(Connection conn, String schema, List<String> tables) throws SQLException;

    List<String> sampleColumn(Connection conn, String schema, String table, String column) throws SQLException;
}
