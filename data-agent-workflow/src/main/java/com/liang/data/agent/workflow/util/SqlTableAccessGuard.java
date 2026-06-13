package com.liang.data.agent.workflow.util;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.connector.DatabaseTypeEnum;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 校验 SQL 访问的物理表是否处于智能体选中的表范围内。
 */
public final class SqlTableAccessGuard {

    private SqlTableAccessGuard() {
    }

    public static Set<String> findUnauthorizedTables(String sql, String databaseType, List<String> allowedTables) {
        if (allowedTables == null || allowedTables.isEmpty()) {
            return Set.of();
        }

        Set<String> normalizedAllowedTables = new LinkedHashSet<>();
        allowedTables.stream()
                .map(SqlTableAccessGuard::normalizeTableName)
                .forEach(normalizedAllowedTables::add);

        Set<String> unauthorizedTables = new LinkedHashSet<>();
        for (String table : extractTables(sql, databaseType)) {
            if (!normalizedAllowedTables.contains(normalizeTableName(table))) {
                unauthorizedTables.add(table);
            }
        }
        return unauthorizedTables;
    }

    private static Set<String> extractTables(String sql, String databaseType) {
        DbType dbType = toDbType(databaseType);
        try {
            SQLStatement statement = SQLUtils.parseSingleStatement(sql, dbType);
            SchemaStatVisitor visitor = SQLUtils.createSchemaStatVisitor(dbType);
            statement.accept(visitor);

            Set<String> tables = new LinkedHashSet<>();
            for (TableStat.Name tableName : visitor.getTables().keySet()) {
                tables.add(tableName.getName());
            }
            return tables;
        } catch (RuntimeException e) {
            throw new ServiceException("SQL 表权限校验失败: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private static DbType toDbType(String databaseType) {
        DatabaseTypeEnum type = DatabaseTypeEnum.of(databaseType)
                .orElseThrow(() -> new ServiceException("不支持的数据库类型: " + databaseType));
        return switch (type) {
            case MYSQL -> DbType.mysql;
            case POSTGRESQL -> DbType.postgresql;
            default -> throw new ServiceException("暂不支持该数据库的 SQL 表权限校验: " + databaseType);
        };
    }

    private static String normalizeTableName(String tableName) {
        String normalized = tableName == null ? "" : tableName.trim();
        int separator = normalized.lastIndexOf('.');
        if (separator >= 0) {
            normalized = normalized.substring(separator + 1);
        }
        return normalized
                .replace("`", "")
                .replace("\"", "")
                .toLowerCase(Locale.ROOT);
    }
}
