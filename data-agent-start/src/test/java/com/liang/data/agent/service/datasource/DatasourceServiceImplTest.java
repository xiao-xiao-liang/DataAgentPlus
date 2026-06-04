package com.liang.data.agent.service.datasource;

import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import com.liang.data.agent.dal.mapper.DatasourceMapper;
import com.liang.data.agent.service.datasource.dto.DatasourceDTO;
import com.liang.data.agent.service.datasource.impl.DatasourceServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据源服务实现单元测试。
 */
class DatasourceServiceImplTest {

    private final DatabaseAccessor databaseAccessor = mock(DatabaseAccessor.class);
    private final DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
    private final DatasourceServiceImpl service = new DatasourceServiceImpl(databaseAccessor);

    DatasourceServiceImplTest() {
        ReflectionTestUtils.setField(service, "baseMapper", datasourceMapper);
    }

    @Test
    void createShouldFillConnectionUrlAndKeepDescription() {
        when(datasourceMapper.insert(any(DatasourceEntity.class))).thenReturn(1);
        when(databaseAccessor.ping(any())).thenReturn(null);
        DatasourceDTO dto = new DatasourceDTO()
                .setName("pg_metro")
                .setType("postgresql")
                .setHost("pg.example.com")
                .setPort(5432)
                .setDatabaseName("metro|public")
                .setUsername("agent")
                .setPassword("secret")
                .setDescription("地铁运行轨迹数据源");

        service.create(dto);

        ArgumentCaptor<DatasourceEntity> captor = ArgumentCaptor.forClass(DatasourceEntity.class);
        verify(datasourceMapper).insert(captor.capture());
        DatasourceEntity saved = captor.getValue();
        assertThat(saved.getConnectionUrl()).isEqualTo("jdbc:postgresql://pg.example.com:5432/metro");
        assertThat(saved.getDescription()).isEqualTo("地铁运行轨迹数据源");
        assertThat(saved.getTestStatus()).isEqualTo("success");
    }

    @Test
    void testConnectionShouldUpdateTestStatusToSuccess() {
        DatasourceEntity entity = new DatasourceEntity();
        entity.setId(7);
        entity.setName("pg_metro");
        entity.setType("postgresql");
        entity.setHost("pg.example.com");
        entity.setPort(5432);
        entity.setDatabaseName("metro|public");
        entity.setUsername("agent");
        entity.setPassword("secret");
        when(datasourceMapper.selectById(7)).thenReturn(entity);
        when(datasourceMapper.updateById(any(DatasourceEntity.class))).thenReturn(1);
        when(databaseAccessor.ping(any())).thenReturn(null);

        String result = service.testConnection(7);

        assertThat(result).isNull();
        ArgumentCaptor<DatasourceEntity> captor = ArgumentCaptor.forClass(DatasourceEntity.class);
        verify(datasourceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTestStatus()).isEqualTo("success");
    }
}
