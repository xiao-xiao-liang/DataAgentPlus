package com.liang.data.agent.dal.connector.dialect;

import org.junit.jupiter.api.Test;

import com.liang.data.agent.dal.connector.bo.DbConfigBO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL 方言单元测试
 *
 * <p>验证 PostgreSQL 方言层的安全 Schema 切换、SQL 审计和分页改写。</p>
 */
class PgDialectTest {

    private final PgDialect pgDialect = new PgDialect();

    @Test
    void shouldEscapeSchemaWhenBuildSwitchSchemaSql() {
        String sql = pgDialect.buildSwitchSchemaSql("tenant\"; DROP TABLE users; --");

        assertThat(sql).isEqualTo("SET search_path TO \"tenant\"\"; DROP TABLE users; --\"");
    }

    @Test
    void shouldAppendDefaultLimitWhenQueryHasNoLimit() {
        String sql = pgDialect.prepareQuerySql("SELECT id, name FROM users");

        assertThat(sql).isEqualTo("SELECT id, name FROM users LIMIT 100");
    }

    @Test
    void shouldKeepQueryWhenLimitAlreadyExists() {
        String sql = pgDialect.prepareQuerySql("SELECT id, name FROM users LIMIT 20");

        assertThat(sql).isEqualTo("SELECT id, name FROM users LIMIT 20");
    }

    @Test
    void shouldAppendDefaultLimitWhenLimitOnlyAppearsInStringLiteral() {
        String sql = pgDialect.prepareQuerySql("SELECT 'limit 20' AS remark FROM users");

        assertThat(sql).isEqualTo("SELECT 'limit 20' AS remark FROM users LIMIT 100");
    }

    @Test
    void shouldRejectNonSelectStatement() {
        assertThatThrownBy(() -> pgDialect.prepareQuerySql("UPDATE users SET name = 'a'"))
                .hasMessageContaining("仅允许执行 SELECT 查询");
    }

    @Test
    void shouldRejectMultipleStatements() {
        assertThatThrownBy(() -> pgDialect.prepareQuerySql("SELECT * FROM users; SELECT * FROM orders"))
                .hasMessageContaining("仅允许执行单条 SELECT 查询");
    }

    @Test
    void shouldRejectDangerousFunctions() {
        assertThatThrownBy(() -> pgDialect.prepareQuerySql("SELECT pg_sleep(10)"))
                .hasMessageContaining("危险函数");
        assertThatThrownBy(() -> pgDialect.prepareQuerySql("SELECT pg_read_file('/etc/passwd')"))
                .hasMessageContaining("危险函数");
        assertThatThrownBy(() -> pgDialect.prepareQuerySql("SELECT dblink_exec('conn', 'delete from users')"))
                .hasMessageContaining("危险函数");
    }

    @Test
    void shouldRejectLockingSelect() {
        assertThatThrownBy(() -> pgDialect.prepareQuerySql("SELECT * FROM users FOR UPDATE"))
                .hasMessageContaining("高危语法");
        assertThatThrownBy(() -> pgDialect.prepareQuerySql("SELECT * FROM users FOR SHARE"))
                .hasMessageContaining("高危语法");
    }

    @Test
    void shouldRejectSecondStatementAfterComment() {
        assertThatThrownBy(() -> pgDialect.prepareQuerySql("SELECT 1; /* 注释绕过 */ UPDATE users SET name = 'a'"))
                .hasMessageContaining("SELECT");
    }

    @Test
    void shouldAllowSafeCommentInSingleSelect() {
        assertThat(pgDialect.prepareQuerySql("SELECT id FROM users /* 仅查询用户编号 */"))
                .isEqualTo("SELECT id FROM users /* 仅查询用户编号 */ LIMIT 100");
    }

    @Test
    void shouldBuildSchemaValidationSql() {
        assertThat(pgDialect.buildSchemaValidationSql())
                .isEqualTo("SELECT 1 FROM information_schema.schemata WHERE schema_name = ? LIMIT 1");
    }

    @Test
    void shouldReturnMessageWhenPostgresqlSchemaMissing() throws Exception {
        DbConfigBO config = new DbConfigBO(null, null, "postgres", "secret", "postgresql", "analytics");

        String message = pgDialect.validateConnected(
                        JdbcValidationStub.connectionWithValidationResult(false),
                        config)
                .orElse("");

        assertThat(message).isEqualTo("连接成功，但 PostgreSQL Schema 不存在，请检查 Schema 配置: analytics");
    }

    @Test
    void shouldPassWhenPostgresqlSchemaExists() throws Exception {
        DbConfigBO config = new DbConfigBO(null, null, "postgres", "secret", "postgresql", "public");

        assertThat(pgDialect.validateConnected(
                JdbcValidationStub.connectionWithValidationResult(true),
                config)).isEmpty();
    }
}
