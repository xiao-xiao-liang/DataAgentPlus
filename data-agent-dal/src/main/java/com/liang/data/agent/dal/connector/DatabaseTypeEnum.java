package com.liang.data.agent.dal.connector;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/**
 * 数据库类型枚举
 * <p>
 * 集中管理各数据库的类型标识、驱动类名、校验 SQL 等差异化配置,
 * 消除散落在 ConnectionPool / DdlExecutor 中的字符串魔法值。
 * <p>
 * 新增数据库类型时, 只需在此处添加一个枚举值即可。
 */
@Getter
@AllArgsConstructor
public enum DatabaseTypeEnum {

    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "SELECT 1"),

    POSTGRESQL("postgresql", "org.postgresql.Driver", "SELECT 1"),

    ORACLE("oracle", "oracle.jdbc.OracleDriver", "SELECT 1 FROM DUAL"),
    ;

    /**
     * 类型标识 (与 DatasourceEntity.type 字段对应, 全小写)
     */
    private final String code;

    /**
     * JDBC 驱动全限定类名
     */
    private final String driver;

    /**
     * 连接校验 SQL (Druid validationQuery)
     */
    private final String validationQuery;

    /**
     * 根据类型标识查找枚举 (忽略大小写)
     *
     * @param code 类型标识, 如 "mysql"
     * @return 对应的枚举值
     */
    public static Optional<DatabaseTypeEnum> of(String code) {
        for (DatabaseTypeEnum value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
