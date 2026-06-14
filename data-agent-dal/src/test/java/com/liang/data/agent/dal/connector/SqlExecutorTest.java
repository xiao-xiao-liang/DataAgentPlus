package com.liang.data.agent.dal.connector;

import com.liang.data.agent.dal.connector.dialect.DatabaseDialect;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SQL 执行器单元测试。
 */
class SqlExecutorTest {

    @Test
    void shouldApplySpecifiedMaximumResultRows() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        DatabaseDialect dialect = mock(DatabaseDialect.class);

        when(connection.createStatement()).thenReturn(statement);
        when(dialect.prepareQuerySql("select * from orders limit 500"))
                .thenReturn("select * from orders limit 500");
        when(statement.executeQuery("select * from orders limit 500"))
                .thenThrow(new SQLException("停止测试执行"));

        assertThatThrownBy(() -> SqlExecutor.execute(
                connection,
                "analytics",
                dialect,
                "select * from orders limit 500",
                25
        )).isInstanceOf(SQLException.class);

        verify(statement).setMaxRows(25);
    }
}
