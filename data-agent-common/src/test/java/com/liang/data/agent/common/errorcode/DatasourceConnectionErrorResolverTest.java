package com.liang.data.agent.common.errorcode;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据源连接错误解析器单元测试
 *
 * <p>验证 SQLState 能够被归一化为稳定的平台错误码与中文提示。</p>
 */
class DatasourceConnectionErrorResolverTest {

    @Test
    void shouldResolvePostgresqlInvalidPassword() {
        DatasourceConnectionError error = DatasourceConnectionErrorResolver.resolve(
                "postgresql",
                new SQLException("乱码错误消息", "28P01")
        );

        assertThat(error.errorCode()).isEqualTo(BaseErrorCode.DATASOURCE_AUTH_FAILED);
        assertThat(error.message()).isEqualTo("连接失败：PostgreSQL 用户名或密码错误，请检查账号密码是否正确");
    }

    @Test
    void shouldResolvePostgresqlDatabaseNotFound() {
        DatasourceConnectionError error = DatasourceConnectionErrorResolver.resolve(
                "postgresql",
                new SQLException("database does not exist", "3D000")
        );

        assertThat(error.errorCode()).isEqualTo(BaseErrorCode.DATASOURCE_DATABASE_NOT_FOUND);
        assertThat(error.message()).isEqualTo("连接失败：PostgreSQL 数据库不存在，请检查数据库名称");
    }

    @Test
    void shouldResolveConnectionFailure() {
        DatasourceConnectionError error = DatasourceConnectionErrorResolver.resolve(
                "mysql",
                new SQLException("Communications link failure", "08001")
        );

        assertThat(error.errorCode()).isEqualTo(BaseErrorCode.DATASOURCE_CONNECTION_FAILED);
        assertThat(error.message()).isEqualTo("连接失败：无法连接到数据库服务，请检查主机地址、端口和网络连通性");
    }

    @Test
    void shouldFallbackToRawMessage() {
        DatasourceConnectionError error = DatasourceConnectionErrorResolver.resolve(
                "mysql",
                new SQLException("其他错误", "HY000")
        );

        assertThat(error.errorCode()).isEqualTo(BaseErrorCode.DATASOURCE_CONNECTION_FAILED);
        assertThat(error.message()).isEqualTo("连接失败：其他错误");
    }
}
