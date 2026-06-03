package com.liang.data.agent.dal.connector;

import com.alibaba.druid.pool.DruidDataSource;
import com.liang.data.agent.common.config.DataAgentProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据源管理器单元测试
 *
 * <p>验证连接池配置能够从外部配置对象应用到 Druid 连接池。</p>
 */
class DataSourceManagerTest {

    @Test
    void shouldApplyPoolPropertiesToDruidDataSource() {
        DataAgentProperties.PoolProperties poolProperties = new DataAgentProperties.PoolProperties();
        poolProperties.setInitialSize(2);
        poolProperties.setMinIdle(3);
        poolProperties.setMaxActive(12);
        poolProperties.setMaxWait(5_000L);

        DruidDataSource dataSource = new DruidDataSource();

        DataSourceManager.applyPoolProperties(dataSource, poolProperties);

        assertThat(dataSource.getInitialSize()).isEqualTo(2);
        assertThat(dataSource.getMinIdle()).isEqualTo(3);
        assertThat(dataSource.getMaxActive()).isEqualTo(12);
        assertThat(dataSource.getMaxWait()).isEqualTo(5_000L);
    }
}
