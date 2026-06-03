package com.liang.data.agent.dal.connector.dialect;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectQuery;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLUnionQuery;
import com.alibaba.druid.sql.parser.ParserException;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.connector.DatabaseTypeEnum;
import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.bo.ForeignKeyInfoBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MySQL 数据库方言实现
 *
 * <p>负责构建 MySQL 连接信息、元数据查询以及查询执行前的 Schema 安全切换。</p>
 */
@Component
public class MysqlDialect implements DatabaseDialect {

    private static final DatabaseTypeEnum DB_TYPE = DatabaseTypeEnum.MYSQL;
    private static final int SAMPLE_LIMIT = 10;
    private static final int DEFAULT_QUERY_LIMIT = 100;

    @Override
    public String type() {
        return DB_TYPE.getCode();
    }

    @Override
    public String driver() {
        return DB_TYPE.getDriver();
    }

    @Override
    public String buildJdbcUrl(DbConfigBO config) {
        // 如果外部传了完整的 url，直接使用；否则根据配置拼接
        if (StringUtils.isNotBlank(config.url())) {
            return config.url();
        }
        return String.format("jdbc:mysql://%s/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true",
                "localhost:3306", // 暂时由于 DbConfigBO 没有 host/port 参数，如果 URL 为空则给个默认值。实际上 config 里的 url 是完整的。
                config.schema());
    }

    @Override
    public String validationQuery() {
        return DB_TYPE.getValidationQuery();
    }

    @Override
    public String buildSwitchSchemaSql(String schema) {
        if (StringUtils.isBlank(schema)) {
            return "";
        }
        return "USE `" + schema.replace("`", "``") + "`";
    }

    @Override
    public String prepareQuerySql(String sql) {
        SQLSelectStatement statement = auditSelectQuery(sql);
        if (hasLimit(statement)) {
            return sql;
        }
        return stripTrailingSemicolon(sql) + " LIMIT " + DEFAULT_QUERY_LIMIT;
    }

    private SQLSelectStatement auditSelectQuery(String sql) {
        List<SQLStatement> statements;
        try {
            statements = SQLUtils.parseStatements(sql, DbType.mysql);
        } catch (ParserException e) {
            throw new ServiceException("SQL 语法解析失败: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }

        if (statements.size() != 1) {
            throw new ServiceException("SQL 安全审计失败: 仅允许执行单条 SELECT 查询");
        }
        if (!(statements.getFirst() instanceof SQLSelectStatement)) {
            throw new ServiceException("SQL 安全审计失败: 仅允许执行 SELECT 查询");
        }
        return (SQLSelectStatement) statements.getFirst();
    }

    private boolean hasLimit(SQLSelectStatement statement) {
        SQLSelect select = statement.getSelect();
        if (select.getLimit() != null) {
            return true;
        }
        SQLSelectQuery query = select.getQuery();
        if (query instanceof SQLSelectQueryBlock queryBlock) {
            return queryBlock.getLimit() != null;
        }
        if (query instanceof SQLUnionQuery unionQuery) {
            return unionQuery.getLimit() != null;
        }
        return false;
    }

    private String stripTrailingSemicolon(String sql) {
        String trimmed = sql.stripTrailing();
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        }
        return trimmed;
    }

    @Override
    public List<TableInfoBO> showTables(Connection conn, String schema, String pattern) throws SQLException {
        List<TableInfoBO> tables = new ArrayList<>();

        // 如果外部没传 pattern 或者传了空字符串，统一转换为 "%" 以查询所有表
        pattern = StringUtils.defaultIfBlank(pattern, "%");

        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getTables(schema, null, pattern, new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String comment = rs.getString("REMARKS");
                tables.add(new TableInfoBO(tableName, comment));
            }
        }

        return tables;
    }

    @Override
    public List<ColumnInfoBO> showColumns(Connection conn, String schema, String table) throws SQLException {
        List<ColumnInfoBO> columns = new ArrayList<>();

        // 先查主键, 用 Set 存起来, 后面判断某个字段是不是主键
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(schema, null, table)) {
            while (pkRs.next()) {
                primaryKeys.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(schema, null, table, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String dataType = rs.getString("TYPE_NAME");
                int columnSize = rs.getInt("COLUMN_SIZE");
                String comment = rs.getString("REMARKS");
                int nullable = rs.getInt("NULLABLE");

                // 拼接完整类型, 如 "VARCHAR(255)"
                String fullType = String.format("%s(%d)", dataType, columnSize);

                columns.add(new ColumnInfoBO(
                        columnName,
                        fullType,
                        comment,
                        nullable == DatabaseMetaData.columnNullable,
                        primaryKeys.contains(columnName)
                ));
            }
        }

        return columns;
    }

    @Override
    public List<ForeignKeyInfoBO> showForeignKeys(Connection conn, String schema, List<String> tables) throws SQLException {
        List<ForeignKeyInfoBO> foreignKeys = new ArrayList<>();

        for (String table : tables) {
            try (ResultSet rs = conn.getMetaData().getImportedKeys(schema, null, table)) {
                while (rs.next()) {
                    foreignKeys.add(new ForeignKeyInfoBO(
                            rs.getString("FKTABLE_NAME"), // 当前表 (FK 所在的表)
                            rs.getString("FKCOLUMN_NAME"), // 当前表的 FK 字段
                            rs.getString("PKTABLE_NAME"), // 关联的表 (PK 所在的表)
                            rs.getString("PKCOLUMN_NAME") // 关联表的 PK 字段
                    ));
                }
            }
        }

        return foreignKeys;
    }

    @Override
    public List<String> sampleColumn(Connection conn, String schema, String table, String column) throws SQLException {
        List<String> samples = new ArrayList<>();

        String sql = String.format(
                "SELECT DISTINCT `%s` FROM `%s`.`%s` WHERE `%s` IS NOT NULL LIMIT %d",
                column, schema, table, column, SAMPLE_LIMIT
        );

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                samples.add(rs.getString(1)); // 取第一列的值
            }
        }

        return samples;
    }
}
