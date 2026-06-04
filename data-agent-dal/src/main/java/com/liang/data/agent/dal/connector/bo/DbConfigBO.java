package com.liang.data.agent.dal.connector.bo;

import com.liang.data.agent.dal.entity.DatasourceEntity;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * 数据源连接配置 (从 DatasourceEntity 转换而来)
 *
 * @param url      JDBC 连接 URL, 如 jdbc:mysql://localhost:3306/mydb
 * @param username 数据库用户名
 * @param password 数据库密码
 * @param type     数据库类型, 如 "mysql"
 * @param schema   库名/Schema名, MySQL 里等同于 database_name
 */
public record DbConfigBO(
        Integer datasourceId,
        String url,
        String username,
        String password,
        String type,
        String schema
) {
    /**
     * 从 DatasourceEntity 转换
     * 如果 connectionUrl 为空, 就根据 host/port/databaseName 拼接
     */
    public static DbConfigBO from(DatasourceEntity entity) {
        String url = entity.getConnectionUrl();
        if (StringUtils.isBlank(url)) {
            url = buildJdbcUrl(entity.getType(), entity.getHost(), entity.getPort(), entity.getDatabaseName());
        }
        return new DbConfigBO(
                entity.getId(),
                url,
                entity.getUsername(),
                entity.getPassword(),
                entity.getType(),
                extractSchemaName(entity.getType(), entity.getDatabaseName())
        );
    }

    /**
     * 按数据库类型构建默认 JDBC URL。
     *
     * @param type 数据库类型
     * @param host 主机地址
     * @param port 端口号
     * @param databaseName 数据库名称
     * @return JDBC 连接 URL
     */
    public static String buildJdbcUrl(String type, String host, Integer port, String databaseName) {
        String databaseType = StringUtils.defaultString(type).toLowerCase(Locale.ROOT);
        String database = extractDatabaseName(databaseType, databaseName);
        if ("postgresql".equals(databaseType)) {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        }
        return String.format("jdbc:%s://%s:%d/%s?useSSL=false&characterEncoding=utf8",
                databaseType, host, port, database);
    }

    /**
     * 提取用于 JDBC URL 的数据库名称。
     *
     * <p>PostgreSQL 支持 database|schema 格式，URL 只使用 database 部分。</p>
     *
     * @param type 数据库类型
     * @param databaseName 原始数据库配置
     * @return 数据库名称
     */
    public static String extractDatabaseName(String type, String databaseName) {
        String databaseType = StringUtils.defaultString(type).toLowerCase(Locale.ROOT);
        String database = StringUtils.defaultString(databaseName);
        if ("postgresql".equals(databaseType) && database.contains("|")) {
            return database.split("\\|", 2)[0];
        }
        return database;
    }

    /**
     * 提取用于元数据和 SQL 执行的 Schema 名称。
     *
     * <p>PostgreSQL 支持 database|schema 格式；未显式配置 schema 时默认使用 public。</p>
     *
     * @param type 数据库类型
     * @param databaseName 原始数据库配置
     * @return Schema 名称
     */
    public static String extractSchemaName(String type, String databaseName) {
        String databaseType = StringUtils.defaultString(type).toLowerCase(Locale.ROOT);
        String database = StringUtils.defaultString(databaseName);
        if (!"postgresql".equals(databaseType)) {
            return database;
        }
        if (!database.contains("|")) {
            return "public";
        }
        String[] parts = database.split("\\|", 2);
        return StringUtils.defaultIfBlank(parts.length > 1 ? parts[1] : "", "public");
    }
}
