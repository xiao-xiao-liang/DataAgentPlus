package com.liang.data.agent.dal.connector.bo;

import com.liang.data.agent.dal.entity.DatasourceEntity;
import org.apache.commons.lang3.StringUtils;

/**
 * 数据源连接配置 (从 DatasourceEntity 转换而来)
 *
 * @param url      JDBC 连接 URL, 如 jdbc:mysql://localhost:3306/mydb
 * @param username 数据库用户名
 * @param password 数据库密码
 * @param type     数据库类型, 如 "mysql"
 * @param schema   库名/Schema名, MySQL 里等同于 database_name
 */
public record DbConfigBO(
        String url,
        String username,
        String password,
        String type,
        String schema
) {
    /**
     * 从 DatasourceEntity 转换
     * 如果 connectionUrl 为空, 就根据 host/port/databaseName 拼接
     */
    public static DbConfigBO from(DatasourceEntity entity) {
        String url = entity.getConnectionUrl();
        if (StringUtils.isBlank(url))
            // 拼接: jdbc:mysql://host:port/databaseName?useSSL=false&characterEncoding=utf8
            url = String.format("jdbc:%s://%s:%d/%s?useSSL=false&characterEncoding=utf8",
                    entity.getType(), entity.getHost(), entity.getPort(), entity.getDatabaseName());
        return new DbConfigBO(
                url,
                entity.getUsername(),
                entity.getPassword(),
                entity.getType(),
                entity.getDatabaseName()
        );
    }
}
