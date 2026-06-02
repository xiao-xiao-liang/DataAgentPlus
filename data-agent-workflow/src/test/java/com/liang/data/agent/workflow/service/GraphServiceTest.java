package com.liang.data.agent.workflow.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.workflow.dto.GraphRequest;
import com.liang.data.agent.workflow.dto.node.IntentRecognitionOutputDTO;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.config.WorkflowConfiguration;
import com.liang.data.agent.workflow.node.ClarificationAskNode;
import com.liang.data.agent.workflow.node.ClarificationNormalizeNode;
import com.liang.data.agent.workflow.util.NodeBeanUtil;
import com.liang.data.agent.workflow.util.FluxUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_ASK_NODE;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_CONFIRM_NODE;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_NORMALIZE_NODE;
import static com.liang.data.agent.common.constant.StateKey.HUMAN_FEEDBACK_NODE;
import static com.liang.data.agent.common.constant.NodeOutputKey.INTENT_RECOGNITION_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_NORMALIZED_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphServiceTest {

    @Test
    void shouldInterruptAfterStreamingClarificationNodes() throws Exception {
        NodeBeanUtil nodeBeanUtil = mock(NodeBeanUtil.class);
        AsyncNodeAction noopNode = AsyncNodeAction.node_async(state -> Map.of());
        AsyncEdgeAction endEdge = AsyncEdgeAction.edge_async(state -> END);
        when(nodeBeanUtil.toAsyncNode(any())).thenReturn(noopNode);
        when(nodeBeanUtil.toAsyncEdge(any())).thenReturn(endEdge);

        StateGraph graph = new WorkflowConfiguration().nl2sqlGraph(nodeBeanUtil);
        GraphService graphService = new GraphService(graph);

        CompiledGraph compiledGraph = (CompiledGraph) ReflectionTestUtils.getField(graphService, "compiledGraph");
        assertTrue(compiledGraph.compileConfig.interruptsBefore().contains(CLARIFICATION_NORMALIZE_NODE));
        assertTrue(compiledGraph.compileConfig.interruptsBefore().contains(CLARIFICATION_CONFIRM_NODE));
        assertTrue(compiledGraph.compileConfig.interruptsBefore().contains(HUMAN_FEEDBACK_NODE));
        assertTrue(compiledGraph.compileConfig.interruptsAfter().contains(CLARIFICATION_ASK_NODE));
        assertTrue(compiledGraph.compileConfig.interruptsAfter().contains(CLARIFICATION_NORMALIZE_NODE));
    }

    @Test
    void shouldUseConfiguredCheckpointSaverWhenProvided() {
        BaseCheckpointSaver checkpointSaver = mock(BaseCheckpointSaver.class);

        CompileConfig compileConfig = GraphService.buildCompileConfig(checkpointSaver);

        assertTrue(compileConfig.checkpointSaver().isPresent());
    }

    @Test
    void shouldResumeFromClarificationAnswerIntoNormalizeNode() throws Exception {
        NodeBeanUtil nodeBeanUtil = mock(NodeBeanUtil.class);
        when(nodeBeanUtil.toAsyncNode(any())).thenAnswer(invocation -> {
            Class<?> nodeClass = invocation.getArgument(0);
            NodeAction action;
            if (nodeClass == ClarificationAskNode.class) {
                action = new ClarificationAskNode();
            } else if (nodeClass == ClarificationNormalizeNode.class) {
                action = this::normalizeWithStreamingOutput;
            } else {
                action = this::stubNodeOutput;
            }
            return AsyncNodeAction.node_async(action);
        });
        when(nodeBeanUtil.toAsyncEdge(any())).thenReturn(AsyncEdgeAction.edge_async(state -> END));

        StateGraph graph = new WorkflowConfiguration().nl2sqlGraph(nodeBeanUtil);
        GraphService graphService = new GraphService(graph);

        GraphRequest firstRequest = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-clarify")
                .query("分析系统链路的核心瓶颈所在")
                .build();
        List<String> firstChunks = graphService.chat(firstRequest, "(无)").collectList().block();
        assertTrue(String.join("", firstChunks).contains("clarification_request"));

        GraphRequest answerRequest = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-clarify")
                .interactionType("CLARIFICATION_ANSWER")
                .interactionContent("按耗时、错误率、调用次数衡量")
                .build();
        List<String> resumedChunks = graphService.chat(answerRequest, "(无)").collectList().block();
        assertEquals("normalized", String.join("", resumedChunks).trim());
    }

    private Map<String, Object> stubNodeOutput(OverAllState state) {
        IntentRecognitionOutputDTO intent = new IntentRecognitionOutputDTO();
        intent.setClassification("《可能的数据分析请求》");

        QueryEnhanceOutputDTO query = new QueryEnhanceOutputDTO();
        query.setCanonicalQuery("分析系统链路的核心瓶颈所在");
        query.setExpandedQueries(List.of("系统链路核心瓶颈"));

        return Map.of(
                INTENT_RECOGNITION_NODE_OUTPUT, intent,
                QUERY_ENHANCE_NODE_OUTPUT, query,
                TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, List.of()
        );
    }

    private Map<String, Object> normalizeWithStreamingOutput(OverAllState state) {
        Flux<ChatResponse> sourceFlux = Flux.just(ChatResponseUtil.createResponse("normalized"));
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                ClarificationNormalizeNode.class,
                state,
                ignored -> Map.of(CLARIFICATION_NORMALIZED_OUTPUT, "normalized"),
                sourceFlux
        );
        return Map.of(CLARIFICATION_NORMALIZED_OUTPUT, generator);
    }
}
