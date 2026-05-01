package com.liang.data.agent.dal.connector;

import com.alibaba.druid.pool.DruidDataSource;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.dialect.DatabaseDialect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一的数据源生命周期管理器
 */
@Slf4j
@Component
public class DataSourceManager implements DisposableBean {

    // 缓存池，Key 为 datasourceId
    private final ConcurrentHashMap<Integer, DruidDataSource> pools = new ConcurrentHashMap<>();

    // 支持的所有方言
    private final Map<String, DatabaseDialect> dialects;

    public DataSourceManager(List<DatabaseDialect> dialectList) {
        this.dialects = dialectList.stream()
                .collect(Collectors.toMap(d -> d.type().toLowerCase(), d -> d));
    }

    /**
     * 获取数据库方言
     */
    public DatabaseDialect getDialect(String type) {
        DatabaseDialect dialect = dialects.get(type.toLowerCase());
        if (dialect == null) {
            throw new ServiceException("系统暂不支持该数据源类型: " + type);
        }
        return dialect;
    }

    /**
     * 测试联通性 (一次性连接，不走池)
     */
    public String ping(DbConfigBO config) {
        DatabaseDialect dialect = getDialect(config.type());
        String url = dialect.buildJdbcUrl(config);
        try (Connection conn = DriverManager.getConnection(url, config.username(), config.password())) {
            return null; // 成功返回 null
        } catch (SQLException e) {
            log.error("连接测试失败。url: {}, sqlState: {}, message: {}", url, e.getSQLState(), e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * 获取连接 (自动懒建池)
     */
    public Connection getConnection(DbConfigBO config) {
        Integer cacheKey = config.datasourceId();
        if (cacheKey == null) {
            throw new ServiceException("获取数据库连接失败: datasourceId 不能为空");
        }

        DatabaseDialect dialect = getDialect(config.type());

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                DruidDataSource dataSource = pools.computeIfAbsent(cacheKey, key -> {
                    log.debug("创建新的 Druid 连接池: datasourceId={}", key);
                    return createDataSource(config, dialect);
                });

                log.debug("获取连接池连接: {}", dataSource);
                return dataSource.getConnection();
            } catch (SQLException e) {
                log.warn("第 {} 次获取连接失败, url={}", attempt, config.url());
                if (attempt == maxRetries) {
                    throw new ServiceException("获取数据库连接失败 (已重试" + maxRetries + "次)");
                }
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("获取连接被中断", ie);
                }
            }
        }
        throw new RuntimeException("获取数据库连接失败");
    }

    /**
     * 驱逐池
     */
    public void evict(Integer datasourceId) {
        if (datasourceId == null) return;
        DruidDataSource old = pools.remove(datasourceId);
        if (old != null) {
            log.info("驱逐闲置/变更的数据源连接池: datasourceId={}", datasourceId);
            old.close();
        }
    }

    private DruidDataSource createDataSource(DbConfigBO config, DatabaseDialect dialect) {
        try {
            DruidDataSource ds = new DruidDataSource();

            ds.setUrl(dialect.buildJdbcUrl(config));
            ds.setUsername(config.username());
            ds.setPassword(config.password());
            ds.setDriverClassName(dialect.driver());

            ds.setInitialSize(5);
            ds.setMinIdle(5);
            ds.setMaxActive(20);
            ds.setMaxWait(10_000);

            ds.setTestOnBorrow(false);
            ds.setTestWhileIdle(true);
            ds.setValidationQuery(dialect.validationQuery());
            ds.setMinEvictableIdleTimeMillis(300_000);
            ds.setTestOnReturn(false);

            ds.setFilters("wall,stat");
            ds.setBreakAfterAcquireFailure(true);
            ds.setConnectionErrorRetryAttempts(2);

            ds.init();
            return ds;
        } catch (SQLException e) {
            throw new ServiceException("创建数据源连接池失败, url=" + config.url(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    @Override
    public void destroy() {
        pools.values().forEach(DruidDataSource::close);
        pools.clear();
        log.info("所有数据源连接池已安全关闭");
    }
}
