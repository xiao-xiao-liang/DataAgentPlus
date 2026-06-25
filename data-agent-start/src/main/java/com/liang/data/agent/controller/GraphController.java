package com.liang.data.agent.controller;

import com.liang.data.agent.common.enums.InteractionType;
import com.liang.data.agent.common.exception.ClientException;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.gateway.context.GatewayExecutionContextFactory;
import com.liang.data.agent.gateway.context.GatewayReactorContext;
import com.liang.data.agent.service.agent.AgentService;
import com.liang.data.agent.service.chat.ChatMessageService;
import com.liang.data.agent.service.chat.ChatSessionService;
import com.liang.data.agent.service.chat.SessionTitleService;
import com.liang.data.agent.service.chat.dto.ChatMessageDTO;
import com.liang.data.agent.service.chat.vo.ChatSessionVO;
import com.liang.data.agent.workflow.dto.GraphRequest;
import com.liang.data.agent.workflow.dto.GraphStreamChunk;
import com.liang.data.agent.workflow.service.GraphService;
import com.liang.data.agent.workflow.service.WorkflowAdmissionService;
import com.liang.data.agent.workflow.service.WorkflowRunService;
import com.liang.data.agent.workflow.util.WorkflowEventUtil;
import com.liang.data.agent.workflow.vo.WorkflowQueueVO;
import com.liang.data.agent.workflow.vo.WorkflowRunVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.liang.data.agent.workflow.constants.WorkflowEventConstants.EVENT_NODE_OUTPUT;
import static com.liang.data.agent.workflow.constants.WorkflowEventConstants.EVENT_PREFIX;

/**
 * 重构后的核心工作流流式图交互控制器。
 *
 * <p>集成会话与消息持久化、多轮历史检索、分析任务排队准入和异步落库保护机制。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private static final Long DEFAULT_USER_ID = 1L;
    private static final Duration QUEUE_RETRY_INTERVAL = Duration.ofSeconds(2);
    private static final Duration QUEUE_MAX_WAIT = Duration.ofMinutes(5);

    private final GraphService graphService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final SessionTitleService sessionTitleService;
    private final AgentService agentService;
    private final WorkflowRunService workflowRunService;
    private final WorkflowAdmissionService workflowAdmissionService;
    private final GatewayExecutionContextFactory contextFactory;

    /**
     * 核心 SSE 流式对话接口。
     *
     * @param request 对话流式请求体
     * @return 返回 String 类型的 SSE 响应流
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody GraphRequest request) {
        String requestQuery = request.getQuery();
        int requestQueryLength = contentLength(requestQuery);
        boolean requestQueryBlank = !StringUtils.hasText(requestQuery);
        log.info("收到图流对话请求，agentId：{}，threadId：{}，interactionType：{}，query长度：{}，query为空：{}",
                request.getAgentId(), request.getThreadId(), request.getInteractionType(),
                requestQueryLength, requestQueryBlank);

        // 1. 校验并解析智能体 ID。
        int agentIdInt = parseAgentId(request);
        agentService.getById(agentIdInt);

        // 2. 校验并自动创建会话。
        String threadId = request.getThreadId();
        boolean isNewSession = false;
        if (!StringUtils.hasText(threadId)) {
            threadId = UUID.randomUUID().toString();
            request.setThreadId(threadId);
            isNewSession = true;
        }

        ChatSessionVO session = chatSessionService.findBySessionId(threadId);
        if (session == null) {
            synchronized (this) {
                session = chatSessionService.findBySessionId(threadId);
                if (session == null) {
                    session = chatSessionService.createSession(agentIdInt, "新会话", null);
                    isNewSession = true;
                }
            }
        }

        // 3. 解析交互类型，并在新分析请求上创建准入队列记录。
        InteractionType interactionType = InteractionType.fromCode(request.getInteractionType());
        if (interactionType == InteractionType.NEW_QUERY && StringUtils.hasText(request.getHumanFeedbackContent())) {
            interactionType = InteractionType.HUMAN_PLAN_FEEDBACK;
        }
        Long userId = normalizeUserId(request.getUserId());
        request.setUserId(userId);
        WorkflowQueueVO queue = null;
        if (interactionType == InteractionType.NEW_QUERY) {
            queue = workflowAdmissionService.enqueue(userId, threadId, agentIdInt, request.getQuery());
        }

        // 4. 保存用户消息，澄清答复需要落库，避免历史会话缺失用户补充内容。
        String userMessageContent = resolveUserMessageContent(request, interactionType);
        if (StringUtils.hasText(userMessageContent)) {
            ChatMessageDTO userMsgDto = ChatMessageDTO.builder()
                    .role("user")
                    .content(userMessageContent)
                    .messageType("text")
                    .build();
            chatMessageService.saveMessage(userMsgDto, threadId);
        }

        // 5. 构建多轮对话上下文。
        String multiTurnContext = "(无)";
        if (!isNewSession) {
            multiTurnContext = chatMessageService.getMultiTurnContext(threadId, 10);
        }

        final String finalSessionId = threadId;
        final String finalMultiTurnContext = multiTurnContext;
        final String userQuery = request.getQuery();
        final boolean triggerTitleGen = isNewSession;
        final InteractionType finalInteractionType = interactionType;
        final int finalAgentId = agentIdInt;
        final WorkflowQueueVO finalQueue = queue;
        final GatewayExecutionContext executionContext = buildExecutionContext(
                finalInteractionType,
                finalSessionId,
                userId,
                finalAgentId
        );

        StringBuilder accumulatedResponse = new StringBuilder();
        StringBuilder completedNodeResponse = new StringBuilder();
        AtomicBoolean cancelHandled = new AtomicBoolean(false);

        Flux<String> queuePrefix = buildQueuePrefix(finalQueue);
        Flux<GraphStreamChunk> streamFlux = Mono.fromRunnable(() -> {
                    if (finalInteractionType == InteractionType.NEW_QUERY) {
                        workflowRunService.startRun(executionContext, request.getQuery());
                    }
                })
                .thenMany(Flux.defer(() -> graphService.chatStream(request, finalMultiTurnContext)))
                .contextWrite(GatewayReactorContext.with(executionContext));

        Flux<String> graphStream = streamFlux
                .doOnNext(event -> {
                    if (isTextOutputEvent(event)) {
                        accumulatedResponse.append(event.content());
                    }
                    if (event.nodeCompleted() && !accumulatedResponse.isEmpty()) {
                        chatMessageService.saveOrUpdateStreamingAssistantMessage(
                                finalSessionId,
                                accumulatedResponse.toString(),
                                "streaming",
                                false
                        );
                        completedNodeResponse.setLength(0);
                        completedNodeResponse.append(accumulatedResponse);
                    }
                })
                .flatMap(event -> {
                    if (isTextOutputEvent(event)) {
                        String workflowEvent = encodeWorkflowStreamEvent(request.getAgentId(), finalSessionId, event);
                        return StringUtils.hasText(workflowEvent)
                                ? Flux.just(event.content(), workflowEvent)
                                : Flux.just(event.content());
                    }
                    if (isWorkflowEventOutput(event)) {
                        return Flux.just(event.content());
                    }
                    String workflowEvent = encodeWorkflowStreamEvent(request.getAgentId(), finalSessionId, event);
                    return StringUtils.hasText(workflowEvent) ? Flux.just(workflowEvent) : Flux.empty();
                })
                .doOnComplete(() -> {
                    String fullResponse = accumulatedResponse.toString();
                    log.info("会话 {} 的图流交互已完成，响应长度：{}", finalSessionId, fullResponse.length());

                    // 6. 保存完整 AI 回复并更新运行状态。
                    chatMessageService.saveOrUpdateStreamingAssistantMessage(
                            finalSessionId,
                            fullResponse,
                            "text",
                            true
                    );
                    workflowRunService.markCompleted(executionContext.runId());
                    completeQueue(finalQueue);

                    // 7. 更新会话时间，并在新会话首轮触发标题生成。
                    chatSessionService.updateSessionTime(finalSessionId);
                    if (triggerTitleGen) {
                        sessionTitleService.scheduleTitleGeneration(finalSessionId, userQuery);
                    }
                })
                .doOnError(err -> {
                    log.error("会话 {} 在图流处理过程中发生错误", finalSessionId, err);

                    // 8. 异步保存错误消息，防止会话流程直接挂起。
                    ChatMessageDTO errorMsgDto = ChatMessageDTO.builder()
                            .role("assistant")
                            .content("系统繁忙，请稍后再试：" + safeMessage(err))
                            .messageType("error")
                            .build();
                    chatMessageService.saveMessageAsync(errorMsgDto, finalSessionId);
                    workflowRunService.markFailed(executionContext.runId(), null, safeMessage(err));
                    failQueue(finalQueue, safeMessage(err));
                })
                .onErrorResume(err -> Flux.just(encodeWorkflowStreamEvent(
                        request.getAgentId(),
                        finalSessionId,
                        GraphStreamChunk.error(safeMessage(err), "")
                )))
                .doOnCancel(() -> {
                    handleClientCancel(
                            finalSessionId,
                            completedNodeResponse,
                            executionContext,
                            finalQueue,
                            cancelHandled
                    );
                });

        return queuePrefix.concatWith(graphStream)
                .doOnCancel(() -> handleClientCancel(
                        finalSessionId,
                        completedNodeResponse,
                        executionContext,
                        finalQueue,
                        cancelHandled
                ))
                .onErrorResume(err -> Flux.just(formatSseData("系统繁忙，请稍后再试：" + safeMessage(err))));
    }

    /**
     * 获取工作流图结构。
     *
     * @return PlantUML 图结构
     */
    @GetMapping("/visualization")
    public String visualization() {
        return graphService.getGraphVisualization();
    }

    private int parseAgentId(GraphRequest request) {
        try {
            if (!StringUtils.hasText(request.getAgentId()) || "default".equals(request.getAgentId())) {
                throw new ClientException("请先选择一个智能体再开始对话");
            }
            return Integer.parseInt(request.getAgentId());
        } catch (NumberFormatException e) {
            throw new ClientException("智能体 ID 格式错误");
        }
    }

    private String resolveUserMessageContent(GraphRequest request, InteractionType interactionType) {
        if (interactionType == InteractionType.HUMAN_PLAN_FEEDBACK
                && request.isRejectedPlan()
                && StringUtils.hasText(request.getHumanFeedbackContent())) {
            return request.getHumanFeedbackContent();
        }
        if (interactionType == InteractionType.HUMAN_PLAN_FEEDBACK && !request.isRejectedPlan()) {
            return "开始任务";
        }
        if (interactionType == InteractionType.CLARIFICATION_ANSWER
                || interactionType == InteractionType.CLARIFICATION_CONFIRM) {
            return request.getInteractionContent();
        }
        if (!StringUtils.hasText(request.getHumanFeedbackContent())
                && interactionType != InteractionType.HUMAN_PLAN_FEEDBACK
                && StringUtils.hasText(request.getQuery())) {
            return request.getQuery();
        }
        return null;
    }

    private Flux<String> buildQueuePrefix(WorkflowQueueVO queue) {
        if (queue == null) {
            return Flux.empty();
        }
        return waitForRunningQueue(queue.getQueueId(), Instant.now().plus(QUEUE_MAX_WAIT));
    }

    private Flux<String> waitForRunningQueue(String queueId, Instant deadline) {
        return Flux.defer(() -> {
            WorkflowQueueVO current = workflowAdmissionService.tryPromote(queueId);
            if (current != null && "RUNNING".equals(current.getStatus())) {
                return Flux.just(formatSseData(formatQueueEvent("queue_running", current)));
            }
            if (Instant.now().isAfter(deadline)) {
                cancelQueue(current, "排队等待超时");
                return Flux.error(new ClientException("分析任务排队等待超时，请稍后重试"));
            }
            return Flux.just(formatSseData(formatQueueEvent("queue_waiting", current)))
                    .concatWith(Mono.delay(QUEUE_RETRY_INTERVAL).thenMany(waitForRunningQueue(queueId, deadline)));
        });
    }

    private String formatQueueEvent(String eventType, WorkflowQueueVO queue) {
        if (queue == null) {
            return "排队状态查询失败";
        }
        return "@@DATA_AGENT_EVENT@@{"
                + "\"eventType\":\"" + eventType + "\","
                + "\"payload\":{"
                + "\"queueId\":\"" + queue.getQueueId() + "\","
                + "\"status\":\"" + queue.getStatus() + "\","
                + "\"aheadTaskCount\":" + queue.getAheadTaskCount() + ","
                + "\"aheadUserCount\":" + queue.getAheadUserCount() + ","
                + "\"runningTaskCount\":" + queue.getRunningTaskCount() + ","
                + "\"maxUserRunningLimit\":" + queue.getMaxUserRunningLimit()
                + "}}@@END_DATA_AGENT_EVENT@@";
    }

    private String formatSseData(String message) {
        return message.replace("\r", " ").replace("\n", " ");
    }

    private void handleClientCancel(String sessionId,
                                    StringBuilder completedNodeResponse,
                                    GatewayExecutionContext executionContext,
                                    WorkflowQueueVO queue,
                                    AtomicBoolean cancelHandled) {
        if (!cancelHandled.compareAndSet(false, true)) {
            return;
        }
        log.info("客户端断开连接，threadId：{}", sessionId);

        // 1. 已完成节点内容先落库，避免用户刷新后丢失稳定输出。
        if (!completedNodeResponse.isEmpty()) {
            chatMessageService.saveOrUpdateStreamingAssistantMessage(
                    sessionId,
                    completedNodeResponse.toString(),
                    "text",
                    true
            );
        }

        // 2. 中断运行记录并释放队列占用，等待排队阶段断开也要清理。
        workflowRunService.markInterrupted(executionContext.runId(), "客户端连接断开");
        cancelQueue(queue, "客户端连接断开");
        graphService.stopStreamProcessing(sessionId);
    }

    private String encodeWorkflowStreamEvent(String agentId, String threadId, GraphStreamChunk event) {
        if (event == null) {
            return "";
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeName", event.nodeName());
        payload.put("content", event.content());
        return WorkflowEventUtil.encode(agentId, threadId, event.eventType(), payload);
    }

    private boolean isTextOutputEvent(GraphStreamChunk event) {
        return event != null
                && EVENT_NODE_OUTPUT.equals(event.eventType())
                && event.hasContent()
                && !event.content().contains(EVENT_PREFIX);
    }

    private boolean isWorkflowEventOutput(GraphStreamChunk event) {
        return event != null
                && EVENT_NODE_OUTPUT.equals(event.eventType())
                && event.hasContent()
                && event.content().contains(EVENT_PREFIX);
    }

    private GatewayExecutionContext buildExecutionContext(InteractionType interactionType,
                                                          String sessionId,
                                                          Long userId,
                                                          Integer agentId) {
        // 1. 新分析请求直接创建新的运行上下文，后续由 startRun 持久化 runId 和 traceId。
        if (interactionType == InteractionType.NEW_QUERY) {
            return contextFactory.create(sessionId, userId, agentId, null);
        }
        // 2. 恢复类请求优先读取最近一次运行记录，确保继续沿用同一 runId 和 traceId。
        WorkflowRunVO latestRun = workflowRunService.findLatest(sessionId);
        if (latestRun != null && StringUtils.hasText(latestRun.getRunId())) {
            return new GatewayExecutionContext(
                    latestRun.getRunId(),
                    latestRun.getTraceId(),
                    StringUtils.hasText(latestRun.getSessionId()) ? latestRun.getSessionId() : sessionId,
                    latestRun.getUserId() != null ? latestRun.getUserId() : userId,
                    latestRun.getAgentId() != null ? latestRun.getAgentId() : agentId,
                    null
            );
        }
        // 3. 旧会话缺失运行编号时使用会话编号作为兼容运行标识，避免生成未持久化随机 runId。
        log.warn("未找到可恢复的工作流运行编号，使用会话编号作为兼容运行标识 - sessionId: {}", sessionId);
        return new GatewayExecutionContext(
                sessionId,
                latestRun != null ? latestRun.getTraceId() : null,
                latestRun != null && StringUtils.hasText(latestRun.getSessionId()) ? latestRun.getSessionId() : sessionId,
                latestRun != null && latestRun.getUserId() != null ? latestRun.getUserId() : userId,
                latestRun != null && latestRun.getAgentId() != null ? latestRun.getAgentId() : agentId,
                null
        );
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || !StringUtils.hasText(throwable.getMessage())) {
            return "对话请求失败";
        }
        return throwable.getMessage();
    }

    private void completeQueue(WorkflowQueueVO queue) {
        if (queue != null) {
            workflowAdmissionService.complete(queue.getQueueId());
        }
    }

    private void failQueue(WorkflowQueueVO queue, String reason) {
        if (queue != null) {
            workflowAdmissionService.fail(queue.getQueueId(), reason);
        }
    }

    private void cancelQueue(WorkflowQueueVO queue, String reason) {
        if (queue != null) {
            workflowAdmissionService.cancel(queue.getQueueId(), reason);
        }
    }

    private Long normalizeUserId(Long userId) {
        return userId != null ? userId : DEFAULT_USER_ID;
    }

    private int contentLength(String content) {
        return content != null ? content.length() : 0;
    }
}
