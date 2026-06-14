package com.liang.data.agent.dal.connector.query.safety;

import com.alibaba.druid.sql.visitor.SQLASTVisitor;
import com.liang.data.agent.dal.connector.query.safety.visitor.MysqlSafetyVisitor;
import com.liang.data.agent.dal.connector.query.safety.visitor.PostgresqlSafetyVisitor;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * SQL 安全审计策略，定义危险函数集合和对应的 AST Visitor 创建方式。
 */
@RequiredArgsConstructor
public final class SqlSafetyPolicy {

    private static final Set<String> COMMON_DANGEROUS_FUNCTIONS = Set.of("sleep", "benchmark");

    private final Set<String> dangerousFunctions;
    private final Function<SqlSafetyInspector, SQLASTVisitor> visitorFactory;

    /**
     * 创建自定义数据库安全策略。
     *
     * @param databaseDangerousFunctions 数据库特有危险函数
     * @param visitorFactory             AST Visitor 创建函数
     * @return SQL 安全策略
     */
    public static SqlSafetyPolicy of(Set<String> databaseDangerousFunctions,
                                     Function<SqlSafetyInspector, SQLASTVisitor> visitorFactory) {
        Set<String> dangerousFunctions = new HashSet<>(COMMON_DANGEROUS_FUNCTIONS);
        dangerousFunctions.addAll(databaseDangerousFunctions);
        return new SqlSafetyPolicy(Set.copyOf(dangerousFunctions), visitorFactory);
    }

    /**
     * 创建 MySQL 安全策略。
     *
     * @return MySQL 安全策略
     */
    public static SqlSafetyPolicy mysql() {
        return of(Set.of("load_file"), MysqlSafetyVisitor::new);
    }

    /**
     * 创建 PostgreSQL 安全策略。
     *
     * @return PostgreSQL 安全策略
     */
    public static SqlSafetyPolicy postgresql() {
        return of(Set.of(
                "pg_sleep", "pg_read_file", "pg_read_binary_file", "pg_ls_dir",
                "dblink_exec", "lo_import", "lo_export"
        ), PostgresqlSafetyVisitor::new);
    }

    SqlSafetyInspector createInspector(SafetyFinding finding) {
        return new SqlSafetyInspector(dangerousFunctions, finding);
    }

    SQLASTVisitor createVisitor(SqlSafetyInspector inspector) {
        return visitorFactory.apply(inspector);
    }
}
