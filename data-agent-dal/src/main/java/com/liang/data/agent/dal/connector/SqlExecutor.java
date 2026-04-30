package com.liang.data.agent.dal.connector;

import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import org.apache.commons.lang3.StringUtils;

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
     * 最多返回的行数，防止 SELECT * 拖垮内存
     */
    private static final int RESULT_SET_LIMIT = 1000;

    /**
     * 查询超时秒数，防止慢查询卡死
     */
    private static final int STATEMENT_TIMEOUT = 30;

    /**
     * 执行 SQL 查询, 返回结构化结果
     *
     * @param conn 数据库连接 (由调用方负责关闭)
     * @param schema     数据库名/Schema名
     * @param sql        要执行的 SQL
     * @return 结构化结果
     */
    public static ResultSetBO execute(Connection conn, String schema, String sql) throws SQLException {
        // 创建 Statement
        try (Statement statement = conn.createStatement()) {
            statement.setMaxRows(RESULT_SET_LIMIT);
            statement.setQueryTimeout(STATEMENT_TIMEOUT);
            
            // 切换到目标数据库
            if (StringUtils.isNotBlank(schema)) {
                conn.setCatalog(schema);
            }

            try (ResultSet rs = statement.executeQuery(sql)) {
                return ResultSetBO.of(rs);
            }
        }
    }
}
