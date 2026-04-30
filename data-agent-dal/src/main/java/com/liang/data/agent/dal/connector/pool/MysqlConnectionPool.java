package com.liang.data.agent.dal.connector.pool;

import com.liang.data.agent.dal.connector.DatabaseTypeEnum;
import org.springframework.stereotype.Component;

/**
 * MySQL 连接池实现
 * 只需要提供驱动类名和类型标识, 其他逻辑全在抽象类里
 */
@Component
public class MysqlConnectionPool extends AbstractDBConnectionPool {

    private static final DatabaseTypeEnum DB_TYPE = DatabaseTypeEnum.MYSQL;

    @Override
    protected String getDriver() {
        return DB_TYPE.getDriver();
    }

    @Override
    protected String getValidationQuery() {
        return DB_TYPE.getValidationQuery();
    }

    @Override
    public boolean supports(String type) {
        return DB_TYPE.getCode().equalsIgnoreCase(type);
    }
}
