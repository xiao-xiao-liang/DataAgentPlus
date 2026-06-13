package com.liang.data.agent.workflow.util;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.connector.DatabaseTypeEnum;

import java.util.Optional;

/**
 * SQL 查询风险检查器，用于在执行前识别可能触发大表全量扫描的查询。
 */
public final class SqlQueryRiskGuard {

    private SqlQueryRiskGuard() {
    }

    /**
     * 检查 SQL 是否存在大表查询风险。
     *
     * @param sql          待检查的 SQL
     * @param databaseType 数据库类型
     * @return 存在风险时返回原因，否则返回空
     */
    public static Optional<String> findRisk(String sql, String databaseType) {
        DbType dbType = toDbType(databaseType);
        try {
            SQLStatement statement = SQLUtils.parseSingleStatement(sql, dbType);
            if (!(statement instanceof SQLSelectStatement)) {
                return Optional.empty();
            }

            // 1. 常量查询不读取物理表，无需增加大表查询限制
            SchemaStatVisitor schemaVisitor = SQLUtils.createSchemaStatVisitor(dbType);
            statement.accept(schemaVisitor);
            if (schemaVisitor.getTables().isEmpty()) {
                return Optional.empty();
            }

            // 2. 收集查询中的过滤、聚合、行数限制和连接信息
            QueryRiskVisitor riskVisitor = new QueryRiskVisitor();
            statement.accept(riskVisitor);
            if (riskVisitor.hasCartesianJoin) {
                return Optional.of("SQL 包含缺少连接条件的笛卡尔连接");
            }

            // 3. 缺少全部保护条件时，要求重新生成查询
            if (!riskVisitor.hasWhere
                    && !riskVisitor.hasAggregate
                    && !riskVisitor.hasGroupBy
                    && !riskVisitor.hasLimit) {
                return Optional.of("SQL 缺少 WHERE、聚合条件或显式 LIMIT，可能触发大表全量扫描");
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new ServiceException("SQL 查询风险检查失败: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private static DbType toDbType(String databaseType) {
        DatabaseTypeEnum type = DatabaseTypeEnum.of(databaseType)
                .orElseThrow(() -> new ServiceException("不支持的数据库类型: " + databaseType));
        return switch (type) {
            case MYSQL -> DbType.mysql;
            case POSTGRESQL -> DbType.postgresql;
            default -> throw new ServiceException("暂不支持该数据库的 SQL 查询风险检查: " + databaseType);
        };
    }

    /**
     * 收集查询风险判断所需的 AST 特征。
     */
    private static final class QueryRiskVisitor extends SQLASTVisitorAdapter {

        private boolean hasWhere;
        private boolean hasAggregate;
        private boolean hasGroupBy;
        private boolean hasLimit;
        private boolean hasCartesianJoin;

        @Override
        public boolean visit(SQLSelectQueryBlock queryBlock) {
            hasWhere |= queryBlock.getWhere() != null;
            hasGroupBy |= queryBlock.getGroupBy() != null;
            hasLimit |= queryBlock.getLimit() != null;
            return true;
        }

        @Override
        public boolean visit(SQLAggregateExpr aggregateExpr) {
            hasAggregate = true;
            return true;
        }

        @Override
        public boolean visit(SQLJoinTableSource joinTableSource) {
            SQLJoinTableSource.JoinType joinType = joinTableSource.getJoinType();
            hasCartesianJoin |= joinType == SQLJoinTableSource.JoinType.CROSS_JOIN
                    || joinType == SQLJoinTableSource.JoinType.COMMA
                    || (joinTableSource.getCondition() == null
                    && joinTableSource.getUsing().isEmpty()
                    && !joinTableSource.isNatural());
            return true;
        }
    }
}
