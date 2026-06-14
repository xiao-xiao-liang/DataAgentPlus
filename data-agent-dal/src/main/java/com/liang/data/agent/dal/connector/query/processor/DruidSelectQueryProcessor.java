package com.liang.data.agent.dal.connector.query.processor;

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
import com.liang.data.agent.dal.connector.query.safety.SqlSelectSafetyAuditor;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 基于 Druid AST 的 SELECT 查询处理器。
 *
 * <p>统一负责 SQL 解析、单条只读校验、安全审计和默认分页限制。</p>
 */
@RequiredArgsConstructor
public final class DruidSelectQueryProcessor implements QuerySqlProcessor {

    private static final int DEFAULT_QUERY_LIMIT = 100;

    private final DbType dbType;
    private final SqlSelectSafetyAuditor safetyAuditor;

    /**
     * 使用指定 Druid 数据库类型和安全审计器创建查询处理器。
     *
     * @param dbType        Druid 数据库类型
     * @param safetyAuditor SELECT 安全审计器
     * @return 查询处理器
     */
    public static DruidSelectQueryProcessor of(DbType dbType, SqlSelectSafetyAuditor safetyAuditor) {
        return new DruidSelectQueryProcessor(dbType, safetyAuditor);
    }

    /**
     * 创建 MySQL 查询处理器。
     */
    public static DruidSelectQueryProcessor mysql() {
        return of(DbType.mysql, SqlSelectSafetyAuditor.mysql());
    }

    /**
     * 创建 PostgreSQL 查询处理器。
     */
    public static DruidSelectQueryProcessor postgresql() {
        return of(DbType.postgresql, SqlSelectSafetyAuditor.postgresql());
    }

    @Override
    public String prepare(String sql) {
        // 1. 解析并校验单条 SELECT 查询
        SQLSelectStatement statement = parseSelectStatement(sql);
        // 2. 执行查询安全审计
        safetyAuditor.audit(statement);
        // 3. 已存在 LIMIT 时保留原始 SQL，否则追加默认 LIMIT
        return hasLimit(statement) ? sql : stripTrailingSemicolon(sql) + " LIMIT " + DEFAULT_QUERY_LIMIT;
    }

    private SQLSelectStatement parseSelectStatement(String sql) {
        List<SQLStatement> statements;
        try {
            statements = SQLUtils.parseStatements(sql, dbType);
        } catch (ParserException e) {
            throw new ServiceException("SQL 语法解析失败: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
        if (statements.size() != 1) {
            throw new ServiceException("SQL 安全审计失败: 仅允许执行单条 SELECT 查询");
        }
        if (!(statements.getFirst() instanceof SQLSelectStatement statement)) {
            throw new ServiceException("SQL 安全审计失败: 仅允许执行 SELECT 查询");
        }
        return statement;
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
}
