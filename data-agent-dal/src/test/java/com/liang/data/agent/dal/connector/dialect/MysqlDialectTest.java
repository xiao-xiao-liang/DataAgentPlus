package com.liang.data.agent.dal.connector.dialect;

import org.junit.jupiter.api.Test;

import com.liang.data.agent.dal.connector.bo.DbConfigBO;

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

    @Test
    void shouldBuildDatabaseValidationSql() {
        assertThat(mysqlDialect.buildDatabaseValidationSql())
                .isEqualTo("SELECT 1 FROM information_schema.schemata WHERE schema_name = ? LIMIT 1");
    }

    @Test
    void shouldReturnMessageWhenMysqlDatabaseMissing() throws Exception {
        DbConfigBO config = new DbConfigBO(null, null, "root", "secret", "mysql", "data_agent");

        String message = mysqlDialect.validateConnected(
                        JdbcValidationStub.connectionWithValidationResult(false),
                        config)
                .orElse("");

        assertThat(message).isEqualTo("连接成功，但 MySQL 数据库不存在，请检查数据库名称: data_agent");
    }

    @Test
    void shouldPassWhenMysqlDatabaseExists() throws Exception {
        DbConfigBO config = new DbConfigBO(null, null, "root", "secret", "mysql", "data_agent");

        assertThat(mysqlDialect.validateConnected(
                JdbcValidationStub.connectionWithValidationResult(true),
                config)).isEmpty();
    }
}
