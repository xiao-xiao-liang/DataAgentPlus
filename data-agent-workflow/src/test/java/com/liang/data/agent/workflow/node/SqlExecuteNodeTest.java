package com.liang.data.agent.workflow.node;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.dal.connector.bo.DisplayStyleBO;
import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
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
                llmService,
                properties,
                mock(JsonParseUtil.class)
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
}
