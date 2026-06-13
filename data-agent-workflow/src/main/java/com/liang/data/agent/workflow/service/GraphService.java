package com.liang.data.agent.workflow.service;

import com.liang.data.agent.workflow.constants.WorkflowRunConstants;

import com.liang.data.agent.workflow.constants.WorkflowEventConstants;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.workflow.util.WorkflowEventUtil;
import com.liang.data.agent.common.enums.InteractionType;
import com.liang.data.agent.workflow.dto.humanfeedback.HumanFeedbackIntent;
import com.liang.data.agent.workflow.dto.humanfeedback.HumanFeedbackIntentResult;
import com.liang.data.agent.workflow.dto.GraphStreamChunk;
import com.liang.data.agent.workflow.dto.GraphRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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

/**
 * 工作流编排服务，负责启动、恢复和流式输出 NL2SQL 分析流程。
 */
@Slf4j
@Service
public class GraphService {

    private final StateGraph nl2sqlGraph;
    private final CompiledGraph compiledGraph;
    private final WorkflowRunService workflowRunService;
    private final HumanFeedbackIntentService humanFeedbackIntentService;

    private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

    public GraphService(StateGraph nl2sqlGraph) {
        this(nl2sqlGraph, WorkflowRunService.noop(), HumanFeedbackIntentService.withoutModel(), (BaseCheckpointSaver) null);
    }

    @Autowired
    public GraphService(StateGraph nl2sqlGraph,
                        WorkflowRunService workflowRunService,
                        HumanFeedbackIntentService humanFeedbackIntentService,
                        ObjectProvider<BaseCheckpointSaver> checkpointSaverProvider) {
        this(nl2sqlGraph, workflowRunService, humanFeedbackIntentService,
                checkpointSaverProvider != null ? checkpointSaverProvider.getIfAvailable() : null);
    }

    GraphService(StateGraph nl2sqlGraph,
                 WorkflowRunService workflowRunService,
                 HumanFeedbackIntentService humanFeedbackIntentService,
                 BaseCheckpointSaver checkpointSaver) {
        this.nl2sqlGraph = nl2sqlGraph;
        this.workflowRunService = workflowRunService;
        this.humanFeedbackIntentService = humanFeedbackIntentService != null
                ? humanFeedbackIntentService
                : HumanFeedbackIntentService.withoutModel();
        try {
            this.compiledGraph = nl2sqlGraph.compile(buildCompileConfig(checkpointSaver));
            log.info("工作流预编译完成，中断点: {}, {}, {}",
                    CLARIFICATION_NORMALIZE_NODE, CLARIFICATION_CONFIRM_NODE, HUMAN_FEEDBACK_NODE);
        } catch (GraphStateException e) {
            log.error("工作流预编译失败", e);
            throw new IllegalStateException("工作流预编译失败", e);
        }
    }

    static CompileConfig buildCompileConfig(BaseCheckpointSaver checkpointSaver) {
        CompileConfig.Builder builder = CompileConfig.builder()
                .interruptBefore(CLARIFICATION_NORMALIZE_NODE, CLARIFICATION_CONFIRM_NODE, HUMAN_FEEDBACK_NODE)
                .interruptAfter(CLARIFICATION_ASK_NODE, CLARIFICATION_NORMALIZE_NODE);
        if (checkpointSaver != null) {
            builder.saverConfig(SaverConfig.builder().register(checkpointSaver).build());
        }
        return builder.build();
    }

    public Flux<String> chat(GraphRequest request, String multiTurnContext) {
        return chatStream(request, multiTurnContext)
                .filter(this::isTextOutputEvent)
                .map(GraphStreamChunk::content);
    }

    /**
     * 执行工作流并返回带节点边界信号的流式事件。
     *
     * @param request          工作流请求
     * @param multiTurnContext 多轮会话上下文
     * @return 工作流流式事件
     */
    public Flux<GraphStreamChunk> chatStream(GraphRequest request, String multiTurnContext) {
        InteractionType interactionType = InteractionType.fromCode(request.getInteractionType());
        if (interactionType == InteractionType.NEW_QUERY && StringUtils.hasText(request.getHumanFeedbackContent())) {
            interactionType = InteractionType.HUMAN_PLAN_FEEDBACK;
        }
        return switch (interactionType) {
            case CONTINUE_ANALYSIS -> handleContinueAnalysis(request, multiTurnContext);
            case CLARIFICATION_ANSWER -> handleClarificationAnswer(request, multiTurnContext);
            case CLARIFICATION_CONFIRM -> handleClarificationConfirm(request, multiTurnContext);
            case HUMAN_PLAN_FEEDBACK -> handleHumanFeedback(request, multiTurnContext);
            default -> handleNewProcess(request, multiTurnContext);
        };
    }

    private Flux<GraphStreamChunk> handleNewProcess(GraphRequest request, String multiTurnContext) {
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

        return streamGraph(threadId, compiledGraph.stream(initialState, config), config, "工作流执行");
    }

    private Flux<GraphStreamChunk> handleContinueAnalysis(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");
        return resume(threadId, stateUpdate, "异常中断恢复");
    }

    private Flux<GraphStreamChunk> handleClarificationAnswer(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(CLARIFICATION_FEEDBACK_DATA, Map.of("answer", safeInteractionContent(request)));
        stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");
        return resume(threadId, stateUpdate, "澄清回答恢复");
    }

    private Flux<GraphStreamChunk> handleClarificationConfirm(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(CLARIFICATION_CONFIRM_DATA, Map.of("content", safeInteractionContent(request)));
        stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");
        return resume(threadId, stateUpdate, "澄清确认恢复");
    }

    private Flux<GraphStreamChunk> handleHumanFeedback(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        String feedbackContent = request.getHumanFeedbackContent() != null ? request.getHumanFeedbackContent() : "";
        boolean approved = resolveHumanFeedbackApproved(request, feedbackContent);
        Map<String, Object> feedbackData = new HashMap<>();
        feedbackData.put("feedback", approved);
        feedbackData.put("feedback_content", feedbackContent);
        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(HUMAN_FEEDBACK_DATA, feedbackData);
        stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");
        return resume(threadId, stateUpdate, "人工反馈恢复", feedbackData);
    }

    private boolean resolveHumanFeedbackApproved(GraphRequest request, String feedbackContent) {
        if (!request.isRejectedPlan()) {
            return true;
        }

        HumanFeedbackIntentResult intentResult = humanFeedbackIntentService.classify(feedbackContent);
        if (intentResult.getIntent() == HumanFeedbackIntent.APPROVE) {
            log.info("人工反馈文本识别为确认意图，按审核通过处理 - threadId: {}, confidence: {}, content: {}",
                    request.getThreadId(), intentResult.getConfidence(), feedbackContent);
            return true;
        }

        log.info("人工反馈文本未识别为确认意图，按驳回修改处理 - threadId: {}, intent: {}, confidence: {}, reason: {}",
                request.getThreadId(), intentResult.getIntent(), intentResult.getConfidence(), intentResult.getReason());
        return false;
    }

    private Flux<GraphStreamChunk> resume(String threadId, Map<String, Object> stateUpdate, String label) {
        return resume(threadId, stateUpdate, label, Map.of());
    }

    private Flux<GraphStreamChunk> resume(String threadId,
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
            return streamGraph(threadId, compiledGraph.stream(null, resumeConfig), resumeConfig, label);
        } catch (Exception e) {
            log.error("{}失败 - threadId: {}", label, threadId, e);
            return Flux.error(e);
        }
    }

    private Flux<GraphStreamChunk> streamGraph(String threadId, Flux<?> graphFlux, RunnableConfig config, String label) {
        StreamContext streamContext = new StreamContext();
        streamContextMap.put(threadId, streamContext);
        AtomicReference<String> currentNode = new AtomicReference<>();

        Flux<GraphStreamChunk> contentEvents = graphFlux
                .concatMap(response -> {
                    if (response instanceof StreamingOutput<?> so) {
                        String chunk = so.chunk();
                        String nodeName = so.node();
                        List<GraphStreamChunk> events = new ArrayList<>();
                        String previousNode = currentNode.get();
                        if (previousNode != null && !previousNode.equals(nodeName)) {
                            persistCheckpoint(threadId, previousNode, config, streamContext);
                            events.add(GraphStreamChunk.nodeCompleted(previousNode));
                        }
                        if (previousNode == null || !previousNode.equals(nodeName)) {
                            events.add(GraphStreamChunk.nodeStarted(nodeName));
                        }
                        currentNode.set(nodeName);
                        if (StringUtils.hasLength(chunk)) {
                            if (isWaitingUserInputEvent(chunk)) {
                                events.add(GraphStreamChunk.waitingUserInput(nodeName, chunk));
                            }
                            events.add(GraphStreamChunk.content(chunk, nodeName));
                        }
                        return Flux.fromIterable(events);
                    }
                    return Flux.empty();
                })
                .doOnNext(event -> {
                    if (isTextOutputEvent(event)) {
                        streamContext.appendOutput(event.content());
                    }
                });

        return contentEvents.publish(sharedContentEvents -> {
                    Flux<GraphStreamChunk> fallbackPersistEvents = Flux.interval(
                                    Duration.ofMillis(WorkflowRunConstants.FALLBACK_PERSIST_INTERVAL_MILLIS))
                            .takeUntilOther(sharedContentEvents.ignoreElements())
                            .doOnNext(ignored -> {
                                String nodeName = currentNode.get();
                                if (nodeName != null
                                        && streamContext.shouldPersistByTimeFallback(System.currentTimeMillis())) {
                                    persistCheckpoint(threadId, nodeName, config, streamContext);
                                }
                            })
                            .thenMany(Flux.empty());
                    return Flux.merge(sharedContentEvents, fallbackPersistEvents);
                })
                .concatWith(Flux.defer(() -> {
                    String nodeName = currentNode.get();
                    if (nodeName == null) {
                        return Flux.just(GraphStreamChunk.done());
                    }
                    return Flux.just(GraphStreamChunk.nodeCompleted(nodeName), GraphStreamChunk.done());
                }))
                .doOnComplete(() -> {
                    String nodeName = currentNode.get();
                    if (nodeName != null) {
                        persistCheckpoint(threadId, nodeName, config, streamContext);
                    }
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

    private boolean isTextOutputEvent(GraphStreamChunk event) {
        return event != null
                && WorkflowEventConstants.EVENT_NODE_OUTPUT.equals(event.eventType())
                && StringUtils.hasLength(event.content())
                && !isWorkflowEventContent(event.content());
    }

    private boolean isWaitingUserInputEvent(String content) {
        return isWorkflowEventContent(content)
                && (content.contains("\"eventType\":\"clarification_request\"")
                || content.contains("\"eventType\":\"clarification_confirmation\"")
                || content.contains("\"eventType\":\"memory_candidate\""));
    }

    private boolean isWorkflowEventContent(String content) {
        return StringUtils.hasLength(content) && content.contains(WorkflowEventConstants.EVENT_PREFIX);
    }

    private void persistCheckpoint(String threadId, String nodeName, RunnableConfig config, StreamContext streamContext) {
        try {
            StateSnapshot snapshot = compiledGraph.lastStateOf(config).orElse(null);
            if (snapshot == null) {
                return;
            }
            String checkpointId = snapshot.config().checkPointId().orElse(null);
            workflowRunService.markNodeCompleted(
                    threadId,
                    nodeName,
                    snapshot.next(),
                    checkpointId,
                    snapshot.state().data(),
                    streamContext.getCollectedOutput()
            );
        } catch (Exception e) {
            log.warn("保存工作流状态快照失败 - threadId: {}, node: {}, reason: {}", threadId, nodeName, e.getMessage());
        }
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
