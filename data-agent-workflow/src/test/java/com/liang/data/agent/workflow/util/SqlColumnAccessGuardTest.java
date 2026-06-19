package com.liang.data.agent.workflow.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 字段访问权限校验测试。
 */
class SqlColumnAccessGuardTest {

    @Test
    void shouldRejectUnauthorizedQualifiedColumn() {
        Set<String> unauthorized = SqlColumnAccessGuard.findUnauthorizedColumns(
                "SELECT o.id, o.secret FROM orders o",
                "mysql",
                Map.of("orders", Set.of("id"))
        );

        assertThat(unauthorized).contains("orders.secret");
    }

    @Test
    void shouldAllowConfiguredColumns() {
        Set<String> unauthorized = SqlColumnAccessGuard.findUnauthorizedColumns(
                "SELECT id, amount FROM orders",
                "mysql",
                Map.of("orders", Set.of("id", "amount"))
        );

        assertThat(unauthorized).isEmpty();
    }
}
