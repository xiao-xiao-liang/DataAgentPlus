package com.liang.data.agent.dal.connector.dialect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
