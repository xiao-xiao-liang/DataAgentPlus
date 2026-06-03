package com.liang.data.agent.dal.connector.dialect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MySQL 方言单元测试
 *
 * <p>验证方言层生成的 SQL 符合安全转义要求。</p>
 */
class MysqlDialectTest {

    private final MysqlDialect mysqlDialect = new MysqlDialect();

    @Test
    void shouldEscapeSchemaWhenBuildSwitchSchemaSql() {
        String sql = mysqlDialect.buildSwitchSchemaSql("tenant`; DROP TABLE user; --");

        assertThat(sql).isEqualTo("USE `tenant``; DROP TABLE user; --`");
    }

    @Test
    void shouldAppendDefaultLimitWhenQueryHasNoLimit() {
        String sql = mysqlDialect.prepareQuerySql("SELECT id, name FROM users");

        assertThat(sql).isEqualTo("SELECT id, name FROM users LIMIT 100");
    }

    @Test
    void shouldKeepQueryWhenLimitAlreadyExists() {
        String sql = mysqlDialect.prepareQuerySql("SELECT id, name FROM users LIMIT 20");

        assertThat(sql).isEqualTo("SELECT id, name FROM users LIMIT 20");
    }

    @Test
    void shouldAppendDefaultLimitWhenLimitOnlyAppearsInStringLiteral() {
        String sql = mysqlDialect.prepareQuerySql("SELECT 'limit 20' AS remark FROM users");

        assertThat(sql).isEqualTo("SELECT 'limit 20' AS remark FROM users LIMIT 100");
    }

    @Test
    void shouldRejectNonSelectStatement() {
        assertThatThrownBy(() -> mysqlDialect.prepareQuerySql("DELETE FROM users"))
                .hasMessageContaining("仅允许执行 SELECT 查询");
    }

    @Test
    void shouldRejectMultipleStatements() {
        assertThatThrownBy(() -> mysqlDialect.prepareQuerySql("SELECT * FROM users; SELECT * FROM orders"))
                .hasMessageContaining("仅允许执行单条 SELECT 查询");
    }
}
