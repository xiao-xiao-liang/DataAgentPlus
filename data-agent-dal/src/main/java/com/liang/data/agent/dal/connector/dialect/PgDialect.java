package com.liang.data.agent.dal.connector.dialect;

import com.liang.data.agent.dal.connector.DatabaseTypeEnum;
import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.bo.ForeignKeyInfoBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;
import com.liang.data.agent.dal.connector.query.processor.DruidSelectQueryProcessor;
import com.liang.data.agent.dal.connector.query.processor.QuerySqlProcessor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * PostgreSQL 数据库方言实现
 *
 * <p>负责 PostgreSQL 连接信息、元数据查询、安全 Schema 切换和查询执行前 SQL 审计。</p>
 */
@Component
public class PgDialect implements DatabaseDialect {

    private static final DatabaseTypeEnum DB_TYPE = DatabaseTypeEnum.POSTGRESQL;
    private static final int SAMPLE_LIMIT = 10;
    private static final QuerySqlProcessor QUERY_SQL_PROCESSOR = DruidSelectQueryProcessor.postgresql();

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
        if (StringUtils.isNotBlank(config.url())) {
            return config.url();
        }
        return String.format("jdbc:postgresql://%s/%s", "localhost:5432", config.schema());
    }

    @Override
    public String validationQuery() {
        return DB_TYPE.getValidationQuery();
    }

    @Override
    public Optional<String> validateConnected(Connection conn, DbConfigBO config) throws SQLException {
        if (StringUtils.isBlank(config.schema())) {
            return Optional.empty();
        }
        try (PreparedStatement statement = conn.prepareStatement(buildSchemaValidationSql())) {
            statement.setString(1, config.schema());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of("连接成功，但 PostgreSQL Schema 不存在，请检查 Schema 配置: " + config.schema());
    }

    /**
     * 构建 PostgreSQL Schema 存在性校验 SQL。
     *
     * @return Schema 校验 SQL
     */
    String buildSchemaValidationSql() {
        return "SELECT 1 FROM information_schema.schemata WHERE schema_name = ? LIMIT 1";
    }

    @Override
    public String buildSwitchSchemaSql(String schema) {
        if (StringUtils.isBlank(schema)) {
            return "";
        }
        return "SET search_path TO \"" + schema.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String prepareQuerySql(String sql) {
        return QUERY_SQL_PROCESSOR.prepare(sql);
    }

    @Override
    public List<TableInfoBO> showTables(Connection conn, String schema, String pattern) throws SQLException {
        List<TableInfoBO> tables = new ArrayList<>();
        pattern = StringUtils.defaultIfBlank(pattern, "%");

        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getTables(null, schema, pattern, new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(new TableInfoBO(
                        rs.getString("TABLE_NAME"),
                        rs.getString("REMARKS")
                ));
            }
        }
        return tables;
    }

    @Override
    public List<ColumnInfoBO> showColumns(Connection conn, String schema, String table) throws SQLException {
        List<ColumnInfoBO> columns = new ArrayList<>();
        Set<String> primaryKeys = new HashSet<>();

        try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(null, schema, table)) {
            while (pkRs.next()) {
                primaryKeys.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        try (ResultSet rs = conn.getMetaData().getColumns(null, schema, table, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String dataType = rs.getString("TYPE_NAME");
                int columnSize = rs.getInt("COLUMN_SIZE");
                String comment = rs.getString("REMARKS");
                int nullable = rs.getInt("NULLABLE");

                columns.add(new ColumnInfoBO(
                        columnName,
                        buildFullType(dataType, columnSize),
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
            try (ResultSet rs = conn.getMetaData().getImportedKeys(null, schema, table)) {
                while (rs.next()) {
                    foreignKeys.add(new ForeignKeyInfoBO(
                            rs.getString("FKTABLE_NAME"),
                            rs.getString("FKCOLUMN_NAME"),
                            rs.getString("PKTABLE_NAME"),
                            rs.getString("PKCOLUMN_NAME")
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
                "SELECT DISTINCT \"%s\" FROM \"%s\".\"%s\" WHERE \"%s\" IS NOT NULL LIMIT %d",
                escapeIdentifier(column), escapeIdentifier(schema), escapeIdentifier(table), escapeIdentifier(column), SAMPLE_LIMIT
        );

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                samples.add(rs.getString(1));
            }
        }
        return samples;
    }

    private String buildFullType(String dataType, int columnSize) {
        if (columnSize <= 0 || dataType.equalsIgnoreCase("text") || dataType.equalsIgnoreCase("jsonb")) {
            return dataType;
        }
        return String.format("%s(%d)", dataType, columnSize);
    }

    private String escapeIdentifier(String identifier) {
        return identifier.replace("\"", "\"\"");
    }
}
