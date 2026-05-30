package com.liang.data.agent.workflow.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlStatementGuardTest {

    @Test
    void shouldRejectMultipleStatementsSeparatedBySemicolon() {
        String sql = """
                SELECT `node_type`, COUNT(*) FROM `t_rag_trace_node` GROUP BY `node_type`;
                SELECT `node_name`, COUNT(*) FROM `t_rag_trace_node` GROUP BY `node_name`
                """;

        assertThat(SqlStatementGuard.isSingleStatementQuery(sql)).isFalse();
    }

    @Test
    void shouldAllowSingleStatementWithCteAndTrailingSemicolon() {
        String sql = """
                WITH stats AS (
                  SELECT `node_type`, AVG(`duration_ms`) AS avg_duration
                  FROM `t_rag_trace_node`
                  GROUP BY `node_type`
                )
                SELECT `node_type`, avg_duration FROM stats;
                """;

        assertThat(SqlStatementGuard.isSingleStatementQuery(sql)).isTrue();
    }

    @Test
    void shouldIgnoreSemicolonInsideStringLiteral() {
        String sql = "SELECT `name` FROM `orders` WHERE `remark` = 'a;b'";

        assertThat(SqlStatementGuard.isSingleStatementQuery(sql)).isTrue();
    }
}
