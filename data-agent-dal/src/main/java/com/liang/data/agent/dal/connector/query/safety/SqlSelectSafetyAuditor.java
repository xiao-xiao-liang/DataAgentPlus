package com.liang.data.agent.dal.connector.query.safety;

import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.visitor.SQLASTVisitor;
import com.liang.data.agent.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;

/**
 * SELECT 查询安全审计器，用于组织安全策略的 AST 遍历和审计结果校验。
 */
@RequiredArgsConstructor
public final class SqlSelectSafetyAuditor {

    private final SqlSafetyPolicy policy;

    /**
     * 使用指定策略创建 SELECT 安全审计器。
     *
     * @param policy SQL 安全策略
     */
    public static SqlSelectSafetyAuditor of(SqlSafetyPolicy policy) {
        return new SqlSelectSafetyAuditor(policy);
    }

    /**
     * 创建 MySQL SELECT 安全审计器。
     */
    public static SqlSelectSafetyAuditor mysql() {
        return of(SqlSafetyPolicy.mysql());
    }

    /**
     * 创建 PostgreSQL SELECT 安全审计器。
     */
    public static SqlSelectSafetyAuditor postgresql() {
        return of(SqlSafetyPolicy.postgresql());
    }

    /**
     * 审计 SELECT 查询中的危险能力。
     *
     * @param statement 已完成基础只读校验的 SELECT 语句
     */
    public void audit(SQLSelectStatement statement) {
        // 1. 根据策略创建单次审计上下文并遍历 AST
        SafetyFinding finding = new SafetyFinding();
        SqlSafetyInspector inspector = policy.createInspector(finding);
        SQLASTVisitor visitor = policy.createVisitor(inspector);
        statement.accept(visitor);

        // 2. 根据审计发现拒绝危险查询
        if (finding.dangerousFunction() != null) {
            throw new ServiceException("SQL 安全审计失败: 禁止使用危险函数 " + finding.dangerousFunction());
        }
        if (finding.hasDangerousClause()) {
            throw new ServiceException("SQL 安全审计失败: 禁止使用方言特有高危语法");
        }
    }
}
