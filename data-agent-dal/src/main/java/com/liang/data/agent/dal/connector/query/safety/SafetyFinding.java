package com.liang.data.agent.dal.connector.query.safety;

/**
 * 单次 SELECT 安全审计发现，用于记录危险函数和高危语法。
 */
public final class SafetyFinding {

    private String dangerousFunction;
    private boolean dangerousClause;

    public void recordDangerousFunction(String functionName) {
        dangerousFunction = functionName;
    }

    public void recordDangerousClause() {
        dangerousClause = true;
    }

    public String dangerousFunction() {
        return dangerousFunction;
    }

    public boolean hasDangerousClause() {
        return dangerousClause;
    }
}
