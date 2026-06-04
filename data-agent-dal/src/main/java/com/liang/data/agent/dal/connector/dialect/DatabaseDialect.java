package com.liang.data.agent.dal.connector.dialect;

import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.bo.ForeignKeyInfoBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

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

    /**
     * 连接成功后的业务校验。
     *
     * <p>用于检查默认 database/schema/catalog 是否存在。默认不做额外校验。</p>
     *
     * @param conn 数据库连接
     * @param config 数据源连接配置
     * @return 校验失败时返回可展示的中文提示，校验通过返回空
     */
    default Optional<String> validateConnected(Connection conn, DbConfigBO config) throws SQLException {
        return Optional.empty();
    }

    /**
     * 构建切换 Schema 的 SQL。
     *
     * <p>不同数据库的 Schema/Catalog 切换语法不同，默认返回空字符串表示无需执行切换。</p>
     */
    default String buildSwitchSchemaSql(String schema) {
        return "";
    }

    /**
     * 按当前方言安全切换 Schema。
     *
     * @param conn 数据库连接
     * @param schema 目标 Schema
     */
    default void switchSchema(Connection conn, String schema) throws SQLException {
        String sql = buildSwitchSchemaSql(schema);
        if (StringUtils.isBlank(sql)) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * 执行前准备查询 SQL。
     *
     * <p>方言可在此完成语法审计、分页改写等执行前安全处理。</p>
     */
    default String prepareQuerySql(String sql) {
        return sql;
    }

    List<TableInfoBO> showTables(Connection conn, String schema, String pattern) throws SQLException;

    List<ColumnInfoBO> showColumns(Connection conn, String schema, String table) throws SQLException;

    List<ForeignKeyInfoBO> showForeignKeys(Connection conn, String schema, List<String> tables) throws SQLException;

    List<String> sampleColumn(Connection conn, String schema, String table, String column) throws SQLException;
}
