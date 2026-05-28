package com.liang.data.agent.workflow.node;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
}