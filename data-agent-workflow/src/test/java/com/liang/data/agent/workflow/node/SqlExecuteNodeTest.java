package com.liang.data.agent.workflow.node;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.connector.bo.DisplayStyleBO;
import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import com.liang.data.agent.dal.entity.AgentEntity;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import com.liang.data.agent.dal.mapper.AgentMapper;
import com.liang.data.agent.dal.mapper.AgentDatasourceMapper;
import com.liang.data.agent.dal.mapper.AgentDatasourceTablesMapper;
import com.liang.data.agent.dal.mapper.DatasourceMapper;
import com.liang.data.agent.service.agentdatasource.AgentDatasourceColumnService;
import com.liang.data.agent.service.ratelimit.ResourceGate;
import com.liang.data.agent.service.ratelimit.ResourcePermit;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.SQL_EXECUTE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.SQL_GENERATE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlExecuteNodeTest {

    @Test
    void shouldFallbackToTableWithoutErrorLogWhenChartEnrichmentTimesOut() {
        DataAgentProperties properties = new DataAgentProperties();
        properties.setEnableSqlResultChart(true);
        properties.setEnrichSqlResultTimeout(1L);

        LlmService llmService = mock(LlmService.class, CALLS_REAL_METHODS);
        when(llmService.call(anyString(), anyString())).thenReturn(Flux.never());

        SqlExecuteNode node = new SqlExecuteNode(
                null,
                null,
                null,
                null,
                null,
                null,
                llmService,
                properties,
                mock(JsonParseUtil.class),
                mock(ResourceGate.class)
        );

        QueryEnhanceOutputDTO queryEnhanceOutputDTO = new QueryEnhanceOutputDTO();
        queryEnhanceOutputDTO.setCanonicalQuery("count chunks");

        OverAllState state = mock(OverAllState.class);
        when(state.value(QUERY_ENHANCE_NODE_OUTPUT)).thenReturn(Optional.of(queryEnhanceOutputDTO));

        Logger logger = (Logger) LoggerFactory.getLogger(SqlExecuteNode.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            DisplayStyleBO result = ReflectionTestUtils.invokeMethod(
                    node,
                    "enrichResultSetWithChartConfig",
                    state,
                    new ResultSetBO(
                            List.of("name", "chunk_count"),
                            List.of(Map.of("name", "kb", "chunk_count", "1"))
                    )
            );

            assertThat(result).isEqualTo(DisplayStyleBO.tableDefault());
            assertThat(appender.list)
                    .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void applyShouldNotExecuteSqlWhenSqlPermitRejected() throws Exception {
        DatabaseAccessor databaseAccessor = mock(DatabaseAccessor.class);
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.SQL_EXECUTION), anyString(), any()))
                .thenReturn(ResourcePermit.rejected(ResourceType.SQL_EXECUTION, "agent-1"));
        SqlExecuteNode node = new SqlExecuteNode(
                databaseAccessor,
                null,
                null,
                null,
                null,
                null,
                mock(LlmService.class),
                new DataAgentProperties(),
                mock(JsonParseUtil.class),
                resourceGate
        );
        OverAllState state = mock(OverAllState.class);
        when(state.value(SQL_GENERATE_OUTPUT)).thenReturn(Optional.of("select 1"));
        when(state.value(AGENT_ID)).thenReturn(Optional.of("1"));

        Map<String, Object> result = node.apply(state);

        assertThat(result).containsKey(SQL_EXECUTE_NODE_OUTPUT);
        verify(databaseAccessor, never()).executeSql(any(), anyString());
    }

    @Test
    void applyShouldNotExecuteSqlWhenQueryUsesUnauthorizedTable() throws Exception {
        DatabaseAccessor databaseAccessor = mock(DatabaseAccessor.class);
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AgentDatasourceMapper agentDatasourceMapper = mock(AgentDatasourceMapper.class);
        AgentDatasourceTablesMapper tablesMapper = mock(AgentDatasourceTablesMapper.class);
        ResourceGate resourceGate = mock(ResourceGate.class);

        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(10);
        datasource.setType("mysql");

        when(resourceGate.tryAcquire(eq(ResourceType.SQL_EXECUTION), anyString(), any()))
                .thenReturn(ResourcePermit.acquired(ResourceType.SQL_EXECUTION, "agent-1", () -> {
                }));
        when(agentDatasourceMapper.getActiveDatasource(1)).thenReturn(10);
        when(agentDatasourceMapper.getActiveBindingId(1)).thenReturn(20);
        when(datasourceMapper.selectById(10)).thenReturn(datasource);
        when(tablesMapper.selectTablesByAgentDatasourceId(20)).thenReturn(List.of("orders"));

        SqlExecuteNode node = new SqlExecuteNode(
                databaseAccessor,
                datasourceMapper,
                agentDatasourceMapper,
                tablesMapper,
                mock(com.liang.data.agent.service.agentdatasource.AgentDatasourceColumnService.class),
                mock(AgentMapper.class),
                mock(LlmService.class),
                new DataAgentProperties(),
                mock(JsonParseUtil.class),
                resourceGate
        );
        OverAllState state = mock(OverAllState.class);
        when(state.value(SQL_GENERATE_OUTPUT)).thenReturn(Optional.of("select * from users"));
        when(state.value(AGENT_ID)).thenReturn(Optional.of("1"));

        Map<String, Object> result = node.apply(state);

        assertThat(result).containsKey(SQL_EXECUTE_NODE_OUTPUT);
        verify(databaseAccessor, never()).executeSql(any(), anyString());
    }

    @Test
    void applyShouldNotExecuteSqlWhenQueryMayScanWholeTable() throws Exception {
        DatabaseAccessor databaseAccessor = mock(DatabaseAccessor.class);
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AgentDatasourceMapper agentDatasourceMapper = mock(AgentDatasourceMapper.class);
        AgentDatasourceTablesMapper tablesMapper = mock(AgentDatasourceTablesMapper.class);
        ResourceGate resourceGate = mock(ResourceGate.class);

        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(10);
        datasource.setType("mysql");

        when(resourceGate.tryAcquire(eq(ResourceType.SQL_EXECUTION), anyString(), any()))
                .thenReturn(ResourcePermit.acquired(ResourceType.SQL_EXECUTION, "agent-1", () -> {
                }));
        when(agentDatasourceMapper.getActiveDatasource(1)).thenReturn(10);
        when(agentDatasourceMapper.getActiveBindingId(1)).thenReturn(20);
        when(datasourceMapper.selectById(10)).thenReturn(datasource);
        when(tablesMapper.selectTablesByAgentDatasourceId(20)).thenReturn(List.of("orders"));

        SqlExecuteNode node = new SqlExecuteNode(
                databaseAccessor,
                datasourceMapper,
                agentDatasourceMapper,
                tablesMapper,
                mock(com.liang.data.agent.service.agentdatasource.AgentDatasourceColumnService.class),
                mock(AgentMapper.class),
                mock(LlmService.class),
                new DataAgentProperties(),
                mock(JsonParseUtil.class),
                resourceGate
        );
        OverAllState state = mock(OverAllState.class);
        when(state.value(SQL_GENERATE_OUTPUT)).thenReturn(Optional.of("select * from orders"));
        when(state.value(AGENT_ID)).thenReturn(Optional.of("1"));

        Map<String, Object> result = node.apply(state);

        assertThat(result).containsKey(SQL_EXECUTE_NODE_OUTPUT);
        verify(databaseAccessor, never()).executeSql(any(), anyString());
    }
    @Test
    void applyShouldUseAgentMaximumResultRowsWhenExecutingSql() throws Exception {
        DatabaseAccessor databaseAccessor = mock(DatabaseAccessor.class);
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AgentDatasourceMapper agentDatasourceMapper = mock(AgentDatasourceMapper.class);
        AgentDatasourceTablesMapper tablesMapper = mock(AgentDatasourceTablesMapper.class);
        AgentMapper agentMapper = mock(AgentMapper.class);
        ResourceGate resourceGate = mock(ResourceGate.class);

        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(10);
        datasource.setType("mysql");
        AgentEntity agent = new AgentEntity();
        agent.setMaxResultRows(25);

        when(resourceGate.tryAcquire(eq(ResourceType.SQL_EXECUTION), anyString(), any()))
                .thenReturn(ResourcePermit.acquired(ResourceType.SQL_EXECUTION, "agent-1", () -> {
                }));
        when(agentDatasourceMapper.getActiveDatasource(1)).thenReturn(10);
        when(agentDatasourceMapper.getActiveBindingId(1)).thenReturn(20);
        when(datasourceMapper.selectById(10)).thenReturn(datasource);
        when(tablesMapper.selectTablesByAgentDatasourceId(20)).thenReturn(List.of("orders"));
        when(agentMapper.selectById(1)).thenReturn(agent);
        when(databaseAccessor.executeSql(any(), eq("select * from orders limit 10"), eq(25)))
                .thenReturn(new ResultSetBO(List.of(), List.of()));

        SqlExecuteNode node = new SqlExecuteNode(
                databaseAccessor,
                datasourceMapper,
                agentDatasourceMapper,
                tablesMapper,
                mock(com.liang.data.agent.service.agentdatasource.AgentDatasourceColumnService.class),
                agentMapper,
                mock(LlmService.class),
                new DataAgentProperties(),
                mock(JsonParseUtil.class),
                resourceGate
        );
        OverAllState state = mock(OverAllState.class);
        when(state.value(SQL_GENERATE_OUTPUT)).thenReturn(Optional.of("select * from orders limit 10"));
        when(state.value(AGENT_ID)).thenReturn(Optional.of("1"));

        node.apply(state);

        verify(databaseAccessor).executeSql(any(), eq("select * from orders limit 10"), eq(25));
    }

    @Test
    void applyShouldSyncColumnPermissionsWhenSelectedTableHasNoColumnConfig() throws Exception {
        DatabaseAccessor databaseAccessor = mock(DatabaseAccessor.class);
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AgentDatasourceMapper agentDatasourceMapper = mock(AgentDatasourceMapper.class);
        AgentDatasourceTablesMapper tablesMapper = mock(AgentDatasourceTablesMapper.class);
        AgentDatasourceColumnService columnService = mock(AgentDatasourceColumnService.class);
        ResourceGate resourceGate = mock(ResourceGate.class);
        DataAgentProperties properties = new DataAgentProperties();
        properties.setEnableSqlResultChart(false);

        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(10);
        datasource.setType("mysql");
        datasource.setHost("localhost");
        datasource.setPort(3306);
        datasource.setDatabaseName("analytics");
        datasource.setUsername("root");
        datasource.setPassword("root");

        when(resourceGate.tryAcquire(eq(ResourceType.SQL_EXECUTION), anyString(), any()))
                .thenReturn(ResourcePermit.acquired(ResourceType.SQL_EXECUTION, "agent-1", () -> {
                }));
        when(agentDatasourceMapper.getActiveDatasource(1)).thenReturn(10);
        when(agentDatasourceMapper.getActiveBindingId(1)).thenReturn(20);
        when(datasourceMapper.selectById(10)).thenReturn(datasource);
        when(tablesMapper.selectTablesByAgentDatasourceId(20)).thenReturn(List.of("orders"));
        when(columnService.listColumns(1, 10, "orders")).thenReturn(List.of());
        when(columnService.getAnalyticColumns(20, List.of("orders")))
                .thenReturn(Map.of("orders", Set.of()))
                .thenReturn(Map.of("orders", Set.of("id")));
        when(databaseAccessor.executeSql(any(), eq("select id from orders limit 10"), anyInt()))
                .thenReturn(new ResultSetBO(List.of("id"), List.of(Map.of("id", "1"))));

        SqlExecuteNode node = new SqlExecuteNode(
                databaseAccessor,
                datasourceMapper,
                agentDatasourceMapper,
                tablesMapper,
                columnService,
                mock(AgentMapper.class),
                mock(LlmService.class),
                properties,
                mock(JsonParseUtil.class),
                resourceGate
        );
        OverAllState state = mock(OverAllState.class);
        when(state.value(SQL_GENERATE_OUTPUT)).thenReturn(Optional.of("select id from orders limit 10"));
        when(state.value(AGENT_ID)).thenReturn(Optional.of("1"));

        node.apply(state);

        verify(columnService).syncColumns(eq(20), any(), eq(List.of("orders")));
        verify(databaseAccessor).executeSql(any(), eq("select id from orders limit 10"), anyInt());
    }

    @Test
    void shouldResolveConfiguredMaximumResultRowsWithinSafeRange() {
        SqlExecuteNode node = new SqlExecuteNode(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new DataAgentProperties(),
                mock(JsonParseUtil.class),
                mock(ResourceGate.class)
        );
        AgentEntity agent = new AgentEntity();

        agent.setMaxResultRows(25);
        Integer configuredRows = ReflectionTestUtils.invokeMethod(node, "resolveMaxResultRows", agent);
        assertThat(configuredRows).isEqualTo(25);

        agent.setMaxResultRows(2000);
        Integer clampedRows = ReflectionTestUtils.invokeMethod(node, "resolveMaxResultRows", agent);
        assertThat(clampedRows).isEqualTo(1000);

        Integer defaultRows = ReflectionTestUtils.invokeMethod(node, "resolveMaxResultRows", (Object) null);
        assertThat(defaultRows).isEqualTo(100);
    }
}
