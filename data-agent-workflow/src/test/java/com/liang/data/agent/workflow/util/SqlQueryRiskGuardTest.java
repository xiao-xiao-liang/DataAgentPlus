package com.liang.data.agent.workflow.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlQueryRiskGuardTest {

    @Test
    void shouldRejectQueryWithoutAnyScanConstraint() {
        assertThat(SqlQueryRiskGuard.findRisk("select * from orders", "mysql"))
                .contains("SQL 缺少 WHERE、聚合条件或显式 LIMIT，可能触发大表全量扫描");
    }

    @Test
    void shouldAllowQueriesWithScanConstraint() {
        assertThat(SqlQueryRiskGuard.findRisk("select * from orders where status = 'paid'", "mysql")).isEmpty();
        assertThat(SqlQueryRiskGuard.findRisk("select status, count(*) from orders group by status", "mysql")).isEmpty();
        assertThat(SqlQueryRiskGuard.findRisk("select * from orders limit 100", "mysql")).isEmpty();
    }

    @Test
    void shouldAllowConstantQuery() {
        assertThat(SqlQueryRiskGuard.findRisk("select 1", "mysql")).isEmpty();
    }

    @Test
    void shouldRejectCartesianJoin() {
        assertThat(SqlQueryRiskGuard.findRisk("select * from orders cross join users limit 10", "mysql"))
                .contains("SQL 包含缺少连接条件的笛卡尔连接");
        assertThat(SqlQueryRiskGuard.findRisk("select * from orders, users limit 10", "mysql"))
                .contains("SQL 包含缺少连接条件的笛卡尔连接");
        assertThat(SqlQueryRiskGuard.findRisk("select * from orders join users limit 10", "mysql"))
                .contains("SQL 包含缺少连接条件的笛卡尔连接");
    }

    @Test
    void shouldSupportPostgresqlQuery() {
        assertThat(SqlQueryRiskGuard.findRisk("select * from orders limit 100", "postgresql")).isEmpty();
    }
}
