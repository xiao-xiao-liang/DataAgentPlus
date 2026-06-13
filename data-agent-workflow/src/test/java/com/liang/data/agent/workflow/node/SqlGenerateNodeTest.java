package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.dal.mapper.AgentDatasourceMapper;
import com.liang.data.agent.dal.mapper.AgentDatasourceTablesMapper;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.dto.node.SqlGenerationDTO;
import com.liang.data.agent.workflow.service.Nl2SqlService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.TABLE_RELATION_OUTPUT;
import static com.liang.data.agent.common.constant.ControlFlowKey.SQL_GENERATE_COUNT;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlGenerateNodeTest {

    @Test
    void shouldPassSelectedTablesToSqlGeneration() throws Exception {
        Nl2SqlService nl2SqlService = mock(Nl2SqlService.class);
        AgentDatasourceMapper agentDatasourceMapper = mock(AgentDatasourceMapper.class);
        AgentDatasourceTablesMapper tablesMapper = mock(AgentDatasourceTablesMapper.class);
        SqlGenerateNode node = new SqlGenerateNode(
                nl2SqlService,
                new DataAgentProperties(),
                agentDatasourceMapper,
                tablesMapper
        );

        QueryEnhanceOutputDTO query = new QueryEnhanceOutputDTO();
        query.setCanonicalQuery("查询订单");
        SchemaDTO schema = new SchemaDTO();
        schema.setTables(List.of());

        OverAllState state = mock(OverAllState.class);
        Map<String, Object> values = Map.of(
                AGENT_ID, "1",
                QUERY_ENHANCE_NODE_OUTPUT, query,
                TABLE_RELATION_OUTPUT, schema
        );
        when(state.value(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(values.get(invocation.getArgument(0))));
        when(state.value(SQL_GENERATE_COUNT, 0)).thenReturn(0);
        when(agentDatasourceMapper.getActiveBindingId(1)).thenReturn(20);
        when(tablesMapper.selectTablesByAgentDatasourceId(20)).thenReturn(List.of("orders", "customers"));
        when(nl2SqlService.generateSql(org.mockito.ArgumentMatchers.any())).thenReturn(Flux.empty());

        node.apply(state);

        ArgumentCaptor<SqlGenerationDTO> captor = ArgumentCaptor.forClass(SqlGenerationDTO.class);
        verify(nl2SqlService).generateSql(captor.capture());
        assertThat(captor.getValue().getAllowedTables()).containsExactly("orders", "customers");
    }
}
