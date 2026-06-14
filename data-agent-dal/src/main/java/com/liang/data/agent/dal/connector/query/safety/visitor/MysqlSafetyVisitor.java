package com.liang.data.agent.dal.connector.query.safety.visitor;

import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.liang.data.agent.dal.connector.query.safety.SqlSafetyInspector;

/**
 * MySQL SELECT 安全访问器，负责遍历通用节点和 MySQL 特有查询节点。
 */
public final class MysqlSafetyVisitor extends MySqlASTVisitorAdapter {

    private final SqlSafetyInspector inspector;

    public MysqlSafetyVisitor(SqlSafetyInspector inspector) {
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
    public boolean visit(MySqlSelectQueryBlock queryBlock) {
        inspector.inspectQueryBlock(queryBlock);
        if (queryBlock.isLockInShareMode()) {
            inspector.recordDangerousClause();
        }
        return true;
    }
}
