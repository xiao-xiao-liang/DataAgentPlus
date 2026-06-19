package com.liang.data.agent.workflow.util;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.connector.DatabaseTypeEnum;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 校验 SQL 是否访问当前智能体禁止参与分析的字段。
 */
public final class SqlColumnAccessGuard {

    private SqlColumnAccessGuard() {
    }

    /**
     * 查找 SQL 中未授权的字段引用。
     */
    public static Set<String> findUnauthorizedColumns(String sql, String databaseType,
                                                       Map<String, Set<String>> analyticColumns) {
        if (analyticColumns == null || analyticColumns.isEmpty()) {
            return Set.of();
        }

        Map<String, Set<String>> normalizedAllowed = analyticColumns.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> normalize(entry.getKey()),
                        entry -> entry.getValue().stream().map(SqlColumnAccessGuard::normalize)
                                .collect(java.util.stream.Collectors.toSet())));
        try {
            DbType dbType = toDbType(databaseType);
            SQLStatement statement = SQLUtils.parseSingleStatement(sql, dbType);
            SchemaStatVisitor visitor = SQLUtils.createSchemaStatVisitor(dbType);
            statement.accept(visitor);

            Set<String> unauthorized = new LinkedHashSet<>();
            for (TableStat.Column column : visitor.getColumns()) {
                String tableName = normalize(column.getTable());
                String columnName = normalize(column.getName());
                if (normalizedAllowed.containsKey(tableName)
                        && !normalizedAllowed.get(tableName).contains(columnName)) {
                    unauthorized.add(column.getTable() + "." + column.getName());
                }
            }
            return unauthorized;
        } catch (RuntimeException e) {
            throw new ServiceException("SQL 字段权限校验失败: " + e.getMessage());
        }
    }

    private static DbType toDbType(String databaseType) {
        return switch (DatabaseTypeEnum.of(databaseType)
                .orElseThrow(() -> new ServiceException("不支持的数据库类型: " + databaseType))) {
            case MYSQL -> DbType.mysql;
            case POSTGRESQL -> DbType.postgresql;
            default -> throw new ServiceException("暂不支持该数据库的 SQL 字段权限校验: " + databaseType);
        };
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        int separator = normalized.lastIndexOf('.');
        if (separator >= 0) {
            normalized = normalized.substring(separator + 1);
        }
        return normalized.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
    }
}
