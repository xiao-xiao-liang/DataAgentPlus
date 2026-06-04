package com.liang.data.agent.dal.connector.bo;

import com.liang.data.agent.dal.entity.DatasourceEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据源连接配置单元测试
 *
 * <p>验证不同数据库类型在缺少完整连接 URL 时能够生成正确的 JDBC URL。</p>
 */
class DbConfigBOTest {

    @Test
    void shouldBuildPostgresqlUrlWithoutMysqlParameters() {
        DatasourceEntity entity = new DatasourceEntity();
        entity.setId(1);
        entity.setType("postgresql");
        entity.setHost("127.0.0.1");
        entity.setPort(5432);
        entity.setDatabaseName("metro_monitor");
        entity.setUsername("postgres");
        entity.setPassword("secret");

        DbConfigBO config = DbConfigBO.from(entity);

        assertThat(config.url()).isEqualTo("jdbc:postgresql://127.0.0.1:5432/metro_monitor");
        assertThat(config.schema()).isEqualTo("public");
    }

    @Test
    void shouldSplitPostgresqlDatabaseAndSchema() {
        DatasourceEntity entity = new DatasourceEntity();
        entity.setId(1);
        entity.setType("postgresql");
        entity.setHost("127.0.0.1");
        entity.setPort(5432);
        entity.setDatabaseName("metro_monitor|analytics");
        entity.setUsername("postgres");
        entity.setPassword("secret");

        DbConfigBO config = DbConfigBO.from(entity);

        assertThat(config.url()).isEqualTo("jdbc:postgresql://127.0.0.1:5432/metro_monitor");
        assertThat(config.schema()).isEqualTo("analytics");
    }

    @Test
    void shouldKeepMysqlDatabaseAsSchema() {
        DatasourceEntity entity = new DatasourceEntity();
        entity.setId(2);
        entity.setType("mysql");
        entity.setHost("127.0.0.1");
        entity.setPort(3306);
        entity.setDatabaseName("data_agent");
        entity.setUsername("root");
        entity.setPassword("secret");

        DbConfigBO config = DbConfigBO.from(entity);

        assertThat(config.url()).isEqualTo("jdbc:mysql://127.0.0.1:3306/data_agent?useSSL=false&characterEncoding=utf8");
        assertThat(config.schema()).isEqualTo("data_agent");
    }
}
