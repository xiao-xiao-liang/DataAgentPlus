package com.liang.data.agent.workflow.service;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.common.enums.InteractionType;
import com.liang.data.agent.workflow.dto.GraphRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.liang.data.agent.common.constant.ModeSwitch.HUMAN_FEEDBACK_DATA;
import static com.liang.data.agent.common.constant.ModeSwitch.HUMAN_REVIEW_ENABLED;
import static com.liang.data.agent.common.constant.ModeSwitch.IS_ONLY_NL2SQL;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_CONFIRM_DATA;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_FEEDBACK_DATA;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_ASK_NODE;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_CONFIRM_NODE;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_NORMALIZE_NODE;
import static com.liang.data.agent.common.constant.StateKey.HUMAN_FEEDBACK_NODE;
import static com.liang.data.agent.common.constant.StateKey.INPUT_KEY;
import static com.liang.data.agent.common.constant.StateKey.MULTI_TURN_CONTEXT;
import static com.liang.data.agent.common.constant.StateKey.THREAD_ID;

@Slf4j
@Service
public class GraphService {

    private final StateGraph nl2sqlGraph;

    private final CompiledGraph compiledGraph;

    private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

    public GraphService(StateGraph nl2sqlGraph) {
        this.nl2sqlGraph = nl2sqlGraph;
        try {
            this.compiledGraph = nl2sqlGraph.compile(
                    CompileConfig.builder()
                            .interruptBefore(CLARIFICATION_NORMALIZE_NODE, CLARIFICATION_CONFIRM_NODE, HUMAN_FEEDBACK_NODE)
                            .interruptAfter(CLARIFICATION_ASK_NODE, CLARIFICATION_NORMALIZE_NODE)
                            .build()
            );
            log.info("工作流预编译完成，中断点: {}, {}, {}",
                    CLARIFICATION_NORMALIZE_NODE, CLARIFICATION_CONFIRM_NODE, HUMAN_FEEDBACK_NODE);
        } catch (GraphStateException e) {
            log.error("工作流预编译失败", e);
            throw new IllegalStateException("工作流预编译失败", e);
        }
    }

    public Flux<String> chat(GraphRequest request, String multiTurnContext) {
        InteractionType interactionType = InteractionType.fromCode(request.getInteractionType());
        if (interactionType == InteractionType.NEW_QUERY && StringUtils.hasText(request.getHumanFeedbackContent())) {
            interactionType = InteractionType.HUMAN_PLAN_FEEDBACK;
        }
        return switch (interactionType) {
            case CLARIFICATION_ANSWER -> handleClarificationAnswer(request, multiTurnContext);
            case CLARIFICATION_CONFIRM -> handleClarificationConfirm(request, multiTurnContext);
            case HUMAN_PLAN_FEEDBACK -> handleHumanFeedback(request, multiTurnContext);
            default -> handleNewProcess(request, multiTurnContext);
        };
    }

    private Flux<String> handleNewProcess(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        boolean nl2sqlOnly = request.isNl2sqlOnly();
        boolean humanReviewEnabled = request.isHumanFeedback() && !nl2sqlOnly;

        Map<String, Object> initialState = new HashMap<>();
        initialState.put(INPUT_KEY, request.getQuery());
        initialState.put(AGENT_ID, request.getAgentId());
        initialState.put(THREAD_ID, threadId);
        initialState.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");
        initialState.put(IS_ONLY_NL2SQL, nl2sqlOnly);
        initialState.put(HUMAN_REVIEW_ENABLED, humanReviewEnabled);

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        log.info("开始执行工作流 - threadId: {}, input: {}, agentId: {}, nl2sqlOnly: {}, humanReviewEnabled: {}",
                threadId, request.getQuery(), request.getAgentId(), nl2sqlOnly, humanReviewEnabled);

        return streamGraph(threadId, compiledGraph.stream(initialState, config), "工作流执行");
    }

    private Flux<String> handleClarificationAnswer(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(CLARIFICATION_FEEDBACK_DATA, Map.of("answer", safeInteractionContent(request)));
        stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");
        return resume(threadId, stateUpdate, "澄清回答恢复");
    }

    private Flux<String> handleClarificationConfirm(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(CLARIFICATION_CONFIRM_DATA, Map.of("content", safeInteractionContent(request)));
        stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");
        return resume(threadId, stateUpdate, "澄清确认恢复");
    }

    private Flux<String> handleHumanFeedback(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        Map<String, Object> feedbackData = Map.of(
                "feedback", !request.isRejectedPlan(),
                "feedback_content", request.getHumanFeedbackContent()
        );
        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(HUMAN_FEEDBACK_DATA, feedbackData);
        stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");
        return resume(threadId, stateUpdate, "人工反馈恢复", feedbackData);
    }

    private Flux<String> resume(String threadId, Map<String, Object> stateUpdate, String label) {
        return resume(threadId, stateUpdate, label, Map.of());
    }

    private Flux<String> resume(String threadId,
                                Map<String, Object> stateUpdate,
                                String label,
                                Map<String, Object> metadata) {
        try {
            RunnableConfig baseConfig = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            RunnableConfig updatedConfig = compiledGraph.updateState(baseConfig, stateUpdate);
            RunnableConfig.Builder resumeBuilder = RunnableConfig.builder(updatedConfig);
            if (!metadata.isEmpty()) {
                resumeBuilder.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, metadata);
            }
            RunnableConfig resumeConfig = resumeBuilder.build();
            return streamGraph(threadId, compiledGraph.stream(null, resumeConfig), label);
        } catch (Exception e) {
            log.error("{}失败 - threadId: {}", label, threadId, e);
            return Flux.error(e);
        }
    }

    private Flux<String> streamGraph(String threadId, Flux<?> graphFlux, String label) {
        StreamContext streamContext = new StreamContext();
        streamContextMap.put(threadId, streamContext);
        return graphFlux
                .map(response -> {
                    if (response instanceof StreamingOutput<?> so) {
                        String chunk = so.chunk();
                        return chunk != null ? chunk : "";
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty())
                .doOnNext(streamContext::appendOutput)
                .doOnComplete(() -> {
                    log.info("{}流式输出结束 - threadId: {}", label, threadId);
                    cleanupStreamContext(threadId);
                })
                .doOnError(e -> {
                    log.error("{}异常 - threadId: {}", label, threadId, e);
                    cleanupStreamContext(threadId);
                })
                .doOnCancel(() -> {
                    log.info("{}被取消 - threadId: {}", label, threadId);
                    cleanupStreamContext(threadId);
                });
    }

    private String safeInteractionContent(GraphRequest request) {
        return StringUtils.hasText(request.getInteractionContent())
                ? request.getInteractionContent()
                : "";
    }

    public void stopStreamProcessing(String threadId) {
        StreamContext context = streamContextMap.remove(threadId);
        if (context != null) {
            context.cleanup();
            log.info("已停止流式处理 - threadId: {}", threadId);
        }
    }

    public String getGraphVisualization() {
        try {
            GraphRepresentation repr = nl2sqlGraph.getGraph(
                    GraphRepresentation.Type.PLANTUML, "nl2sql workflow");
            return repr.content();
        } catch (Exception e) {
            log.error("获取图结构失败", e);
            return "error: " + e.getMessage();
        }
    }

    private void cleanupStreamContext(String threadId) {
        StreamContext context = streamContextMap.remove(threadId);
        if (context != null) {
            context.cleanup();
        }
    }
}
