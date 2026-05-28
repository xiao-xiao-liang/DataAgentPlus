package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;

import static com.liang.data.agent.common.constant.ControlFlowKey.PLAN_CURRENT_STEP;
import static com.liang.data.agent.common.constant.NodeOutputKey.PLANNER_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.SQL_EXECUTE_NODE_OUTPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportGeneratorNodeTest {

    @Test
    void shouldUseExecutingStepWhenReadingReportSummary() throws Exception {
        LlmService llmService = mock(LlmService.class);
        when(llmService.callUser(anyString()))
                .thenReturn(Flux.just(ChatResponseUtil.createPureResponse("report")));

        QueryEnhanceOutputDTO queryEnhanceOutputDTO = new QueryEnhanceOutputDTO();
        queryEnhanceOutputDTO.setCanonicalQuery("count chunks");

        String expectedSummary = "use the report generator summary from step two";
        String planJson = """
                {
                  "thought_process": "plan",
                  "execution_plan": [
                    {
                      "step": 1,
                      "tool_to_use": "sql_generate",
                      "tool_parameters": {
                        "instruction": "query data"
                      }
                    },
                    {
                      "step": 2,
                      "tool_to_use": "report_generator",
                      "tool_parameters": {
                        "summary_and_recommendations": "%s"
                      }
                    }
                  ]
                }
                """.formatted(expectedSummary);

        OverAllState state = mock(OverAllState.class);
        when(state.value(PLANNER_NODE_OUTPUT)).thenReturn(Optional.of(planJson));
        when(state.value(QUERY_ENHANCE_NODE_OUTPUT)).thenReturn(Optional.of(queryEnhanceOutputDTO));
        when(state.value(SQL_EXECUTE_NODE_OUTPUT)).thenReturn(Optional.of(Map.of("step_1", "{\"data\":[]}")));
        when(state.value(PLAN_CURRENT_STEP)).thenReturn(Optional.of(3));
        when(state.value(PLAN_CURRENT_STEP, 1)).thenReturn(3);

        new ReportGeneratorNode(llmService).apply(state);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llmService).callUser(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains(expectedSummary);
    }
}
