package com.liang.data.agent.workflow.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlTableAccessGuardTest {

    @Test
    void shouldAllowSelectedTablesInJoin() {
        String sql = """
                SELECT o.id, c.name
                FROM orders o
                JOIN customers c ON c.id = o.customer_id
                """;

        assertThat(SqlTableAccessGuard.findUnauthorizedTables(
                sql, "mysql", List.of("orders", "customers")
        )).isEmpty();
    }

    @Test
    void shouldRejectTableOutsideSelectedScope() {
        String sql = "SELECT * FROM orders o JOIN users u ON u.id = o.user_id";

        assertThat(SqlTableAccessGuard.findUnauthorizedTables(
                sql, "mysql", List.of("orders")
        )).containsExactly("users");
    }

    @Test
    void shouldTreatEmptySelectedTablesAsAllTablesAllowed() {
        assertThat(SqlTableAccessGuard.findUnauthorizedTables(
                "SELECT * FROM any_table", "mysql", List.of()
        )).isEmpty();
    }

    @Test
    void shouldSupportSchemaQualifiedPostgresqlTable() {
        assertThat(SqlTableAccessGuard.findUnauthorizedTables(
                "SELECT * FROM public.orders", "postgresql", List.of("orders")
        )).isEmpty();
    }

    @Test
    void shouldIgnoreCteAlias() {
        String sql = """
                WITH paid_orders AS (
                    SELECT * FROM orders WHERE status = 'paid'
                )
                SELECT * FROM paid_orders
                """;

        assertThat(SqlTableAccessGuard.findUnauthorizedTables(
                sql, "mysql", List.of("orders")
        )).isEmpty();
    }
}
