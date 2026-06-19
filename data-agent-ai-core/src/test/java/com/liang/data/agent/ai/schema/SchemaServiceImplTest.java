package com.liang.data.agent.ai.schema;

import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Schema 服务实现单元测试。
 */
class SchemaServiceImplTest {

    @Test
    void shouldIncludeColumnDetailsInTableDocumentContent() throws Exception {
        SchemaServiceImpl service = new SchemaServiceImpl(
                mock(VectorStore.class),
                mock(DataAgentProperties.class),
                mock(DatabaseAccessor.class),
                mock(com.liang.data.agent.ai.vectorstore.AgentVectorStoreService.class),
                mock(com.liang.data.agent.dal.mapper.AgentDatasourceMapper.class),
                mock(com.liang.data.agent.dal.mapper.DatasourceTableColumnMapper.class)
        );
        Method method = SchemaServiceImpl.class.getDeclaredMethod(
                "buildTableDocument",
                Integer.class,
                Integer.class,
                String.class,
                String.class,
                List.class,
                Map.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Document document = (Document) method.invoke(
                service,
                1,
                2,
                "metro_history_trajectories",
                "地铁列车历史运行轨迹表",
                List.of(
                        new ColumnInfoBO("delay_seconds", "integer", "延误时长（秒）", false, false),
                        new ColumnInfoBO("actual_start_time", "timestamp", "实际出发时间", false, false)
                ),
                Map.of()
        );

        assertThat(document.getText())
                .contains("metro_history_trajectories")
                .contains("地铁列车历史运行轨迹表")
                .contains("delay_seconds")
                .contains("延误时长")
                .contains("actual_start_time")
                .contains("实际出发时间");
    }
}
