package com.liang.data.agent.dal.connector.query.safety.visitor;

import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.dialect.postgresql.ast.stmt.PGSelectQueryBlock;
import com.alibaba.druid.sql.dialect.postgresql.visitor.PGASTVisitorAdapter;
import com.liang.data.agent.dal.connector.query.safety.SqlSafetyInspector;

/**
 * PostgreSQL SELECT 安全访问器，负责遍历通用节点和 PostgreSQL 特有查询节点。
 */
public final class PostgresqlSafetyVisitor extends PGASTVisitorAdapter {

    private final SqlSafetyInspector inspector;

    public PostgresqlSafetyVisitor(SqlSafetyInspector inspector) {
        this.inspector = inspector;
    }

    @Override
    public boolean visit(SQLMethodInvokeExpr methodInvokeExpr) {
        inspector.inspectFunction(methodInvokeExpr);
        return true;
    }

    @Override
    public boolean visit(SQLSelectQueryBlock queryBlock) {
        inspector.inspectQueryBlock(queryBlock);
        return true;
    }

    @Override
    public boolean visit(PGSelectQueryBlock.ForClause forClause) {
        inspector.recordDangerousClause();
        return true;
    }
}
