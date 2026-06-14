package com.liang.data.agent.dal.connector.query.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Druid SELECT 查询处理器单元测试。
 */
class DruidSelectQueryProcessorTest {

    @Test
    void shouldAppendDefaultLimitForMysqlQuery() {
        QuerySqlProcessor processor = DruidSelectQueryProcessor.mysql();

        assertThat(processor.prepare("SELECT id FROM users;"))
                .isEqualTo("SELECT id FROM users LIMIT 100");
    }

    @Test
    void shouldKeepPostgresqlQueryWithLimit() {
        QuerySqlProcessor processor = DruidSelectQueryProcessor.postgresql();

        assertThat(processor.prepare("SELECT id FROM users LIMIT 20"))
                .isEqualTo("SELECT id FROM users LIMIT 20");
    }

    @Test
    void shouldIgnoreLimitInsideStringLiteral() {
        QuerySqlProcessor processor = DruidSelectQueryProcessor.mysql();

        assertThat(processor.prepare("SELECT 'limit 20' AS remark FROM users"))
                .isEqualTo("SELECT 'limit 20' AS remark FROM users LIMIT 100");
    }

    @Test
    void shouldRejectNonSelectAndMultipleStatements() {
        QuerySqlProcessor processor = DruidSelectQueryProcessor.postgresql();

        assertThatThrownBy(() -> processor.prepare("UPDATE users SET name = 'a'"))
                .hasMessageContaining("仅允许执行 SELECT 查询");
        assertThatThrownBy(() -> processor.prepare("SELECT 1; SELECT 2"))
                .hasMessageContaining("仅允许执行单条 SELECT 查询");
    }
}
