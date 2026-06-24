package com.liang.data.agent.workflow.service;

import com.liang.data.agent.workflow.constants.WorkflowEventConstants;

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
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.gateway.context.GatewayReactorContext;
import com.liang.data.agent.workflow.dto.GraphRequest;
import com.liang.data.agent.workflow.dto.GraphStreamChunk;
import com.liang.data.agent.workflow.dto.node.IntentRecognitionOutputDTO;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.config.WorkflowConfiguration;
import com.liang.data.agent.workflow.node.ClarificationAskNode;
import com.liang.data.agent.workflow.node.ClarificationNormalizeNode;
import com.liang.data.agent.workflow.util.NodeBeanUtil;
import com.liang.data.agent.workflow.util.FluxUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_ASK_NODE;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_CONFIRM_NODE;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_NORMALIZE_NODE;
import static com.liang.data.agent.common.constant.StateKey.HUMAN_FEEDBACK_NODE;
import static com.liang.data.agent.common.constant.NodeOutputKey.INTENT_RECOGNITION_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_NORMALIZED_OUTPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void shouldPersistLongRunningNodeByTimeFallbackBeforeNodeCompleted() throws Exception {
        AsyncNodeAction noopNode = AsyncNodeAction.node_async(state -> Map.of());
        StateGraph graph = new StateGraph("long_stream_graph", () -> Map.of())
                .addNode("LongStreamingNode", AsyncNodeAction.node_async(this::longRunningStreamingNode))
                .addNode(CLARIFICATION_ASK_NODE, noopNode)
                .addNode(CLARIFICATION_NORMALIZE_NODE, noopNode)
                .addNode(CLARIFICATION_CONFIRM_NODE, noopNode)
                .addNode(HUMAN_FEEDBACK_NODE, noopNode)
                .addEdge(START, "LongStreamingNode")
                .addEdge("LongStreamingNode", END);
        WorkflowRunService workflowRunService = mock(WorkflowRunService.class);
        GraphService graphService = new GraphService(graph, workflowRunService,
                HumanFeedbackIntentService.withoutModel(), (BaseCheckpointSaver) null);

        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-long-stream")
                .query("长耗时节点流式输出")
                .build();
        GatewayExecutionContext context = new GatewayExecutionContext(
                "run-long-stream", "trace-long-stream", "thread-long-stream", 1L, 2, null);

        List<String> chunks = graphService.chat(request, "(无)")
                .contextWrite(GatewayReactorContext.with(context))
                .collectList()
                .block();

        assertEquals("第一段第二段", String.join("", chunks).replaceAll("\\R", ""));
        ArgumentCaptor<String> accumulatedContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(workflowRunService, atLeast(2)).markNodeCompleted(
                eq("run-long-stream"), anyString(), any(), any(), anyMap(), accumulatedContentCaptor.capture());
        List<String> savedContents = accumulatedContentCaptor.getAllValues().stream()
                .map(content -> content.replaceAll("\\R", ""))
                .toList();
        assertTrue(savedContents.contains("第一段"));
        assertEquals("第一段第二段", savedContents.getLast());
    }

    @Test
    void shouldEmitWorkflowDoneEventWhenStreamCompletes() throws Exception {
        AsyncNodeAction noopNode = AsyncNodeAction.node_async(state -> Map.of());
        StateGraph graph = new StateGraph("done_event_graph", () -> Map.of())
                .addNode("LongStreamingNode", AsyncNodeAction.node_async(this::longRunningStreamingNode))
                .addNode(CLARIFICATION_ASK_NODE, noopNode)
                .addNode(CLARIFICATION_NORMALIZE_NODE, noopNode)
                .addNode(CLARIFICATION_CONFIRM_NODE, noopNode)
                .addNode(HUMAN_FEEDBACK_NODE, noopNode)
                .addEdge(START, "LongStreamingNode")
                .addEdge("LongStreamingNode", END);
        GraphService graphService = new GraphService(graph, WorkflowRunService.noop(),
                HumanFeedbackIntentService.withoutModel(), (BaseCheckpointSaver) null);

        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-done-event")
                .query("长耗时节点流式输出")
                .build();

        List<GraphStreamChunk> chunks = graphService.chatStream(request, "(无)").collectList().block();

        assertThat(chunks).isNotNull();
        assertThat(chunks.getLast().eventType()).isEqualTo(WorkflowEventConstants.EVENT_WORKFLOW_DONE);
        assertThat(chunks.getLast().hasContent()).isFalse();
        assertThat(chunks.get(chunks.size() - 2).eventType()).isEqualTo(WorkflowEventConstants.EVENT_NODE_COMPLETED);
    }

    @Test
    void shouldEmitNodeStartedEventBeforeNodeOutput() throws Exception {
        AsyncNodeAction noopNode = AsyncNodeAction.node_async(state -> Map.of());
        StateGraph graph = new StateGraph("node_started_graph", () -> Map.of())
                .addNode("LongStreamingNode", AsyncNodeAction.node_async(this::longRunningStreamingNode))
                .addNode(CLARIFICATION_ASK_NODE, noopNode)
                .addNode(CLARIFICATION_NORMALIZE_NODE, noopNode)
                .addNode(CLARIFICATION_CONFIRM_NODE, noopNode)
                .addNode(HUMAN_FEEDBACK_NODE, noopNode)
                .addEdge(START, "LongStreamingNode")
                .addEdge("LongStreamingNode", END);
        GraphService graphService = new GraphService(graph, WorkflowRunService.noop(),
                HumanFeedbackIntentService.withoutModel(), (BaseCheckpointSaver) null);

        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-node-started")
                .query("长耗时节点流式输出")
                .build();

        List<GraphStreamChunk> chunks = graphService.chatStream(request, "(无)").collectList().block();

        assertThat(chunks).isNotNull();
        assertThat(chunks.getFirst().eventType()).isEqualTo(WorkflowEventConstants.EVENT_NODE_STARTED);
        assertThat(chunks.getFirst().nodeName()).isEqualTo("LongStreamingNode");
        assertThat(chunks.get(1).eventType()).isEqualTo(WorkflowEventConstants.EVENT_NODE_OUTPUT);
    }

    @Test
    void shouldEmitStructuredErrorAndMarkFailedWhenWorkflowTimesOut() throws Exception {
        AsyncNodeAction noopNode = AsyncNodeAction.node_async(state -> Map.of());
        StateGraph graph = new StateGraph("timeout_graph", () -> Map.of())
                .addNode("LongStreamingNode", AsyncNodeAction.node_async(this::longRunningStreamingNode))
                .addNode(CLARIFICATION_ASK_NODE, noopNode)
                .addNode(CLARIFICATION_NORMALIZE_NODE, noopNode)
                .addNode(CLARIFICATION_CONFIRM_NODE, noopNode)
                .addNode(HUMAN_FEEDBACK_NODE, noopNode)
                .addEdge(START, "LongStreamingNode")
                .addEdge("LongStreamingNode", END);
        WorkflowRunService workflowRunService = mock(WorkflowRunService.class);
        GraphService graphService = new GraphService(graph, workflowRunService,
                HumanFeedbackIntentService.withoutModel(), null, Duration.ofMillis(50));
        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-timeout")
                .query("超时测试")
                .build();
        GatewayExecutionContext context = new GatewayExecutionContext(
                "run-timeout", "trace-timeout", "thread-timeout", 1L, 2, null);

        List<GraphStreamChunk> chunks = graphService.chatStream(request, "(无)")
                .contextWrite(GatewayReactorContext.with(context))
                .collectList()
                .block();

        assertThat(chunks).isNotNull();
        assertThat(chunks.getLast().eventType()).isEqualTo(WorkflowEventConstants.EVENT_WORKFLOW_ERROR);
        assertThat(chunks.getLast().content()).contains("B000100").contains("工作流执行超时");
        verify(workflowRunService).markFailed("run-timeout", "LongStreamingNode", "工作流执行超时");
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
        List<GraphStreamChunk> firstChunks = graphService.chatStream(firstRequest, "(无)").collectList().block();
        assertThat(firstChunks).isNotNull();
        assertThat(firstChunks).anySatisfy(chunk -> assertThat(chunk.content()).contains("clarification_request"));

        GraphRequest answerRequest = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-clarify")
                .interactionType("CLARIFICATION_ANSWER")
                .interactionContent("按耗时、错误率、调用次数衡量")
                .build();
        List<String> resumedChunks = graphService.chat(answerRequest, "(无)").collectList().block();
        assertEquals("normalized", String.join("", resumedChunks).trim());
    }

    @Test
    void shouldEmitWaitingUserInputWhenClarificationEventAppears() throws Exception {
        NodeBeanUtil nodeBeanUtil = mock(NodeBeanUtil.class);
        when(nodeBeanUtil.toAsyncNode(any())).thenAnswer(invocation -> {
            Class<?> nodeClass = invocation.getArgument(0);
            NodeAction action = nodeClass == ClarificationAskNode.class
                    ? new ClarificationAskNode()
                    : this::stubNodeOutput;
            return AsyncNodeAction.node_async(action);
        });
        when(nodeBeanUtil.toAsyncEdge(any())).thenReturn(AsyncEdgeAction.edge_async(state -> END));

        StateGraph graph = new WorkflowConfiguration().nl2sqlGraph(nodeBeanUtil);
        GraphService graphService = new GraphService(graph);

        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-waiting-input")
                .query("分析系统链路的核心瓶颈所在")
                .build();

        List<GraphStreamChunk> chunks = graphService.chatStream(request, "(无)").collectList().block();

        assertThat(chunks).isNotNull();
        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.eventType()).isEqualTo(WorkflowEventConstants.EVENT_WAITING_USER_INPUT);
            assertThat(chunk.nodeName()).isEqualTo("ClarificationAskNode");
            assertThat(chunk.content()).contains("clarification_request");
        });
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

    private Map<String, Object> longRunningStreamingNode(OverAllState state) {
        Flux<ChatResponse> sourceFlux = Flux.just(ChatResponseUtil.createResponse("第一段"))
                .concatWith(Mono.delay(Duration.ofMillis(2100))
                        .map(ignored -> ChatResponseUtil.createResponse("第二段")));
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                LongStreamingNode.class,
                state,
                ignored -> Map.of("long_output", "done"),
                sourceFlux
        );
        return Map.of("long_output", generator);
    }

    private static class LongStreamingNode implements NodeAction {
        @Override
        public Map<String, Object> apply(OverAllState state) {
            return Map.of();
        }
    }
}
