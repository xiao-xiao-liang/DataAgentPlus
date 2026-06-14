package com.liang.data.agent.dal.connector.query.safety;

import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import lombok.RequiredArgsConstructor;

import java.util.Locale;
import java.util.Set;

/**
 * SQL 安全节点检查器，用于执行可跨数据库复用的 AST 节点检查。
 */
@RequiredArgsConstructor
public final class SqlSafetyInspector {

    private final Set<String> dangerousFunctions;
    private final SafetyFinding finding;

    public void inspectFunction(SQLMethodInvokeExpr methodInvokeExpr) {
        String functionName = methodInvokeExpr.getMethodName().toLowerCase(Locale.ROOT);
        if (dangerousFunctions.contains(functionName)) {
            finding.recordDangerousFunction(functionName);
        }
    }

    public void inspectQueryBlock(SQLSelectQueryBlock queryBlock) {
        if (queryBlock.getInto() != null || queryBlock.isForUpdate() || queryBlock.isForShare()) {
            finding.recordDangerousClause();
        }
    }

    public void recordDangerousClause() {
        finding.recordDangerousClause();
    }
}
