package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.gateway.constants.ModelGatewayConstant;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.liang.data.agent.common.constant.ControlFlowKey.PLAN_CURRENT_STEP;
import static com.liang.data.agent.common.constant.NodeOutputKey.PLANNER_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.SQL_EXECUTE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.RESULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportGeneratorNodeTest {

    @Test
    void shouldUseExecutingStepWhenReadingReportSummary() throws Exception {
        LlmService llmService = mock(LlmService.class);
        when(llmService.callUser(eq(ModelGatewayConstant.REPORT_GENERATION), anyString()))
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
        org.mockito.Mockito.verify(llmService).callUser(eq(ModelGatewayConstant.REPORT_GENERATION), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains(expectedSummary);
    }

    @Test
    void shouldRemoveOuterMarkdownFenceWhenStreamingReport() throws Exception {
        LlmService llmService = mock(LlmService.class);
        when(llmService.callUser(eq(ModelGatewayConstant.REPORT_GENERATION), anyString()))
                .thenReturn(Flux.just(ChatResponseUtil.createPureResponse("""
                        ```markdown
                        # 系统会话全景分析报告

                        ```sql
                        SELECT COUNT(*) FROM t_conversation;
                        ```

                        ```echarts
                        {"series":[{"type":"bar","data":[1]}]}
                        ```
                        ```
                        """)));

        OverAllState state = createReportState();

        Map<String, Object> result = new ReportGeneratorNode(llmService).apply(state);

        @SuppressWarnings("unchecked")
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator =
                (Flux<GraphResponse<StreamingOutput<ChatResponse>>>) result.get(RESULT);
        List<GraphResponse<StreamingOutput<ChatResponse>>> responses = generator.collectList().block();
        String streamedContent = responses.stream()
                .filter(response -> !response.isDone())
                .map(response -> response.getOutput().join().chunk())
                .reduce("", String::concat);

        assertThat(streamedContent).contains("$$$markdown-report# 系统会话全景分析报告");
        assertThat(streamedContent).doesNotContain("$$$markdown-report```markdown");
        assertThat(streamedContent).contains("```sql");
        assertThat(streamedContent).contains("```echarts");
    }

    @Test
    void shouldStreamReportChunkBeforeModelResponseCompletes() throws Exception {
        LlmService llmService = mock(LlmService.class);
        when(llmService.callUser(eq(ModelGatewayConstant.REPORT_GENERATION), anyString()))
                .thenReturn(Flux.just(ChatResponseUtil.createPureResponse("第一段"))
                        .concatWith(Mono.delay(Duration.ofSeconds(5))
                                .map(ignored -> ChatResponseUtil.createPureResponse("第二段"))));

        Map<String, Object> result = new ReportGeneratorNode(llmService).apply(createReportState());

        @SuppressWarnings("unchecked")
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator =
                (Flux<GraphResponse<StreamingOutput<ChatResponse>>>) result.get(RESULT);
        String firstReportChunk = generator
                .filter(response -> !response.isDone())
                .map(response -> response.getOutput().join().chunk())
                .skipUntil(chunk -> chunk.contains("$$$markdown-report"))
                .skip(1)
                .filter(chunk -> !chunk.contains("$$$/markdown-report"))
                .blockFirst(Duration.ofMillis(500));

        assertThat(firstReportChunk).isEqualTo("第一段");
    }

    private OverAllState createReportState() {
        QueryEnhanceOutputDTO queryEnhanceOutputDTO = new QueryEnhanceOutputDTO();
        queryEnhanceOutputDTO.setCanonicalQuery("count chunks");

        String planJson = """
                {
                  "thought_process": "plan",
                  "execution_plan": [
                    {
                      "step": 1,
                      "tool_to_use": "report_generator",
                      "tool_parameters": {
                        "summary_and_recommendations": "summary"
                      }
                    }
                  ]
                }
                """;

        OverAllState state = mock(OverAllState.class);
        when(state.value(PLANNER_NODE_OUTPUT)).thenReturn(Optional.of(planJson));
        when(state.value(QUERY_ENHANCE_NODE_OUTPUT)).thenReturn(Optional.of(queryEnhanceOutputDTO));
        when(state.value(SQL_EXECUTE_NODE_OUTPUT)).thenReturn(Optional.of(Map.of("step_1", "{\"data\":[]}")));
        when(state.value(PLAN_CURRENT_STEP)).thenReturn(Optional.of(2));
        when(state.value(PLAN_CURRENT_STEP, 1)).thenReturn(2);
        return state;
    }
}
