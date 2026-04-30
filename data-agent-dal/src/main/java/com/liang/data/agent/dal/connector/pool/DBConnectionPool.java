package com.liang.data.agent.dal.connector.pool;

import com.liang.data.agent.dal.connector.bo.DbConfigBO;

import java.sql.Connection;

/**
 * 数据库连接池接口
 */
public interface DBConnectionPool extends AutoCloseable {

    /**
     * 测试数据库连接是否可用 (不走池, 一次性连接)
     * 用于用户点击"测试连接"按钮时
     *
     * @param config 数据库连接配置
     * @return 成功返回 null, 失败返回错误信息
     */
    String ping(DbConfigBO config);

    /**
     * 从数据库连接池中获取一个连接
     *
     * @param config 数据库连接配置
     * @return 数据库连接
     */
    Connection getConnection(DbConfigBO config);

    /**
     * 判断是否支持该类型的数据源
     */
    boolean supports(String type);
}
