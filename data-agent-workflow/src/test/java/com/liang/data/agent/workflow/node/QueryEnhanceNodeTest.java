package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.gateway.api.ModelGatewayScenes;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.liang.data.agent.common.constant.NodeOutputKey.EVIDENCE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.INPUT_KEY;
import static com.liang.data.agent.common.constant.StateKey.MULTI_TURN_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryEnhanceNodeTest {

    @Test
    void shouldParseQueryEnhanceResult() {
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);
        QueryEnhanceOutputDTO dto = new QueryEnhanceOutputDTO();
        dto.setCanonicalQuery("分析 2026-05 华东 GMV 下降原因");
        dto.setExpandedQueries(List.of("华东 GMV 下降原因", "2026-05 华东 GMV 趋势"));
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(QueryEnhanceOutputDTO.class))).thenReturn(dto);

        QueryEnhanceNode node = new QueryEnhanceNode(mock(LlmService.class), jsonParseUtil);

        Map<String, ?> result = ReflectionTestUtils.invokeMethod(
                node,
                "handleQueryEnhance",
                "```json\n{\"canonical_query\":\"分析 2026-05 华东 GMV 下降原因\",\"expanded_queries\":[\"华东 GMV 下降原因\",\"2026-05 华东 GMV 趋势\"]}\n```"
        );

        assertEquals(dto, result.get(QUERY_ENHANCE_NODE_OUTPUT));
    }

    @Test
    void shouldReturnEmptyMapWhenQueryEnhanceParsingFails() {
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(QueryEnhanceOutputDTO.class)))
                .thenThrow(new IllegalArgumentException("bad json"));

        QueryEnhanceNode node = new QueryEnhanceNode(mock(LlmService.class), jsonParseUtil);

        Map<String, ?> result = ReflectionTestUtils.invokeMethod(
                node,
                "handleQueryEnhance",
                "not-json"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRetryOnceWhenQueryEnhanceResponseIsEmpty() throws Exception {
        LlmService llmService = mock(LlmService.class);
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);
        OverAllState state = mock(OverAllState.class);
        QueryEnhanceOutputDTO dto = new QueryEnhanceOutputDTO();
        dto.setCanonicalQuery("查询列车准点率");
        dto.setExpandedQueries(List.of("统计列车准时到达比例"));

        when(state.value(INPUT_KEY)).thenReturn(Optional.of("列车准时到达的比例是多少"));
        when(state.value(EVIDENCE_OUTPUT)).thenReturn(Optional.of("准点率为准时记录数占总记录数的比例"));
        when(state.value(MULTI_TURN_CONTEXT)).thenReturn(Optional.of("(无)"));
        when(llmService.callUser(eq(ModelGatewayScenes.QUERY_ENHANCE), anyString()))
                .thenReturn(
                        Flux.just(ChatResponseUtil.createPureResponse("")),
                        Flux.just(ChatResponseUtil.createPureResponse(
                                "{\"canonical_query\":\"查询列车准点率\",\"expanded_queries\":[\"统计列车准时到达比例\"]}"))
                );
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(QueryEnhanceOutputDTO.class))).thenReturn(dto);

        QueryEnhanceNode node = new QueryEnhanceNode(llmService, jsonParseUtil);
        Map<String, Object> result = node.apply(state);
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator =
                (Flux<GraphResponse<StreamingOutput<ChatResponse>>>) result.get(QUERY_ENHANCE_NODE_OUTPUT);

        generator.blockLast();

        verify(llmService, times(2)).callUser(eq(ModelGatewayScenes.QUERY_ENHANCE), anyString());
        verify(jsonParseUtil).tryConvertToObject(anyString(), eq(QueryEnhanceOutputDTO.class));
    }
}
