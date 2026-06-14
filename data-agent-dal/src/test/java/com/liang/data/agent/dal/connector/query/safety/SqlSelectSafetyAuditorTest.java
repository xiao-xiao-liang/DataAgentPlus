package com.liang.data.agent.dal.connector.query.safety;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SELECT 查询安全审计器单元测试。
 */
class SqlSelectSafetyAuditorTest {

    @Test
    void shouldRejectMysqlDangerousFunctionAndClause() {
        SqlSelectSafetyAuditor auditor = SqlSelectSafetyAuditor.mysql();

        assertThatThrownBy(() -> auditor.audit(parse("SELECT LOAD_FILE('/etc/passwd')", DbType.mysql)))
                .hasMessageContaining("危险函数");
        assertThatThrownBy(() -> auditor.audit(parse("SELECT * FROM users LOCK IN SHARE MODE", DbType.mysql)))
                .hasMessageContaining("高危语法");
    }

    @Test
    void shouldRejectPostgresqlDangerousFunctionAndClause() {
        SqlSelectSafetyAuditor auditor = SqlSelectSafetyAuditor.postgresql();

        assertThatThrownBy(() -> auditor.audit(parse("SELECT pg_read_file('/etc/passwd')", DbType.postgresql)))
                .hasMessageContaining("危险函数");
        assertThatThrownBy(() -> auditor.audit(parse("SELECT * FROM users FOR UPDATE", DbType.postgresql)))
                .hasMessageContaining("高危语法");
    }

    private SQLSelectStatement parse(String sql, DbType dbType) {
        return (SQLSelectStatement) SQLUtils.parseSingleStatement(sql, dbType);
    }
}
