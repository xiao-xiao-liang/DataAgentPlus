package com.liang.data.agent.dal.connector;

import com.liang.data.agent.common.constant.SqlQueryLimitConstant;
import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import com.liang.data.agent.dal.connector.dialect.DatabaseDialect;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQL 执行器
 */
public class SqlExecutor {
    
    public SqlExecutor() {
    }

    /**
     * 查询超时秒数，防止慢查询卡死
     */
    private static final int STATEMENT_TIMEOUT = 30;

    /**
     * 执行 SQL 查询, 返回结构化结果
     *
     * @param conn 数据库连接 (由调用方负责关闭)
     * @param schema     数据库名/Schema名
     * @param dialect    数据库方言
     * @param sql        要执行的 SQL
     * @return 结构化结果
     */
    public static ResultSetBO execute(Connection conn, String schema, DatabaseDialect dialect, String sql) throws SQLException {
        return execute(conn, schema, dialect, sql, SqlQueryLimitConstant.MAX_RESULT_ROWS);
    }

    /**
     * 执行 SQL 查询，并强制限制最大返回行数。
     *
     * @param conn 数据库连接
     * @param schema 数据库名或 Schema
     * @param dialect 数据库方言
     * @param sql 待执行 SQL
     * @param maxRows 最大返回行数
     * @return 结构化查询结果
     */
    public static ResultSetBO execute(Connection conn, String schema, DatabaseDialect dialect, String sql, int maxRows)
            throws SQLException {
        dialect.switchSchema(conn, schema);
        String preparedSql = dialect.prepareQuerySql(sql);

        // 创建 Statement
        try (Statement statement = conn.createStatement()) {
            statement.setMaxRows(maxRows);
            statement.setQueryTimeout(STATEMENT_TIMEOUT);

            try (ResultSet rs = statement.executeQuery(preparedSql)) {
                return ResultSetBO.of(rs);
            }
        }
    }
}
