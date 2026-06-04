package com.liang.data.agent.common.errorcode;

import java.sql.SQLException;
import java.util.Locale;

/**
 * 数据源连接错误解析器
 *
 * <p>根据 JDBC SQLState 与数据源类型，将底层连接异常转换为平台错误码和稳定的用户提示。</p>
 */
public final class DatasourceConnectionErrorResolver {

    private static final String POSTGRESQL = "postgresql";

    private DatasourceConnectionErrorResolver() {
    }

    /**
     * 解析数据源连接异常
     *
     * @param databaseType 数据库类型
     * @param exception SQL 异常
     * @return 数据源连接错误信息
     */
    public static DatasourceConnectionError resolve(String databaseType, SQLException exception) {
        String type = databaseType == null ? "" : databaseType.toLowerCase(Locale.ROOT);
        String sqlState = exception.getSQLState();

        if (POSTGRESQL.equals(type) && "28P01".equals(sqlState)) {
            return new DatasourceConnectionError(
                    BaseErrorCode.DATASOURCE_AUTH_FAILED,
                    "连接失败：PostgreSQL 用户名或密码错误，请检查账号密码是否正确"
            );
        }
        if ("28000".equals(sqlState)) {
            return new DatasourceConnectionError(
                    BaseErrorCode.DATASOURCE_AUTH_FAILED,
                    "连接失败：用户名或密码错误，请检查账号密码是否正确"
            );
        }
        if (POSTGRESQL.equals(type) && "3D000".equals(sqlState)) {
            return new DatasourceConnectionError(
                    BaseErrorCode.DATASOURCE_DATABASE_NOT_FOUND,
                    "连接失败：PostgreSQL 数据库不存在，请检查数据库名称"
            );
        }
        if ("42501".equals(sqlState)) {
            return new DatasourceConnectionError(
                    BaseErrorCode.DATASOURCE_PERMISSION_DENIED,
                    "连接失败：数据库权限不足，请检查账号权限"
            );
        }
        if (sqlState != null && sqlState.startsWith("08")) {
            return new DatasourceConnectionError(
                    BaseErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "连接失败：无法连接到数据库服务，请检查主机地址、端口和网络连通性"
            );
        }
        return new DatasourceConnectionError(
                BaseErrorCode.DATASOURCE_CONNECTION_FAILED,
                "连接失败：" + exception.getMessage()
        );
    }
}
