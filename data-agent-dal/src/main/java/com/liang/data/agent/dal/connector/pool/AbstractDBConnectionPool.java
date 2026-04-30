package com.liang.data.agent.dal.connector.pool;

import com.alibaba.druid.pool.DruidDataSource;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractDBConnectionPool implements DBConnectionPool {

    // 缓存 DataSource (连接池)
    private static final ConcurrentHashMap<String, DataSource> DATA_SOURCE_CACHE = new ConcurrentHashMap<>();

    /**
     * 子类提供 JDBC 驱动类名
     */
    protected abstract String getDriver();

    /**
     * 子类提供连接校验 SQL (不同数据库不一样, 如 Oracle 是 "SELECT 1 FROM DUAL")
     */
    protected abstract String getValidationQuery();

    @Override
    public String ping(DbConfigBO config) {
        // 用 JDBC 原生方式连接
        try (Connection conn = DriverManager.getConnection(config.url(), config.username(), config.password())) {
            return null; // 能走到这里说明连接成功
        } catch (SQLException e) {
            // 连接失败, 返回错误信息
            log.error("连接测试失败。url：{}，sqlState：{}，message：{}", config.url(), e.getSQLState(), e.getMessage());
            return e.getMessage();
        }
    }

    @Override
    public Connection getConnection(DbConfigBO config) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 1.生成缓存 Key
                String cacheKey = generateCacheKey(config);

                // 2.从缓存中获取 DataSource
                DataSource dataSource = DATA_SOURCE_CACHE.computeIfAbsent(cacheKey, key -> {
                    log.debug("创建新的 Druid 连接池: {}", key);
                    return createDataSource(config);
                });

                // 记录连接池状态
                log.debug("获取连接池连接: {}", dataSource);

                // 3.从 DataSource 借一个 Connection
                return dataSource.getConnection();
            } catch (SQLException e) {
                log.warn("第 {} 次获取连接失败, url={}", attempt, config.url());
                if (attempt == maxRetries)
                    throw new ServiceException("获取数据库连接失败 (已重试" + maxRetries + "次)");

                //  指数退避: 第1次等1秒, 第2次等2秒, 第3次等3秒
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();  // 恢复中断标志
                    throw new RuntimeException("获取连接被中断", ie);
                }
            }
        }
        // 实际上不会走到这里 (循环内必定 return 或 throw), 但编译器需要
        throw new RuntimeException("获取数据库连接失败");
    }

    private String generateCacheKey(DbConfigBO config) {
        return String.format("%s-%s-%s", config.type(), config.username(), config.url());
    }

    private DataSource createDataSource(DbConfigBO config) {
        try {
            DruidDataSource ds = new DruidDataSource();

            // 基础连接信息
            ds.setUrl(config.url());
            ds.setUsername(config.username());
            ds.setPassword(config.password());
            ds.setDriverClassName(getDriver()); // 子类提供, 如 "com.mysql.cj.jdbc.Driver"

            // 连接池大小配置
            ds.setInitialSize(5); // 初始连接数
            ds.setMinIdle(5); // 最小空闲连接数
            ds.setMaxActive(20); // 最大活跃连接数
            ds.setMaxWait(10_000); // 获取连接最大等待时间

            // 连接健康检查
            ds.setTestOnBorrow(false); // 借连接时不检测 (性能优先)
            ds.setTestWhileIdle(true); // 空闲时检测 (推荐)
            ds.setValidationQuery(getValidationQuery()); // 子类提供, 不同库不一样
            ds.setMinEvictableIdleTimeMillis(300_000); // 空闲超过 5 分钟回收
            ds.setTestOnReturn(false); // 归还连接时不检测

            // SQL 防火墙 + 监控统计
            ds.setFilters("wall,stat");

            // 失败后不再重试创建 (避免无限循环)
            ds.setBreakAfterAcquireFailure(true); // 获取失败后标记为不可用
            ds.setConnectionErrorRetryAttempts(2); // 创建连接最多重试 2 次

            return ds;
        } catch (SQLException e) {
            throw new ServiceException("创建数据源连接池失败, url=" + config.url(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    @Override
    public void close() throws Exception {
        DATA_SOURCE_CACHE.values().forEach(ds -> {
            if (ds instanceof DruidDataSource druid) {
                druid.close();
            }
        });
        DATA_SOURCE_CACHE.clear();
        log.info("数据源连接池已关闭");
    }
}
