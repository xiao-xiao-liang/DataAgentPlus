package com.liang.data.agent.workflow.service;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
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
import static com.liang.data.agent.common.constant.StateKey.*;

/**
 * 工作流 SSE 服务
 *
 * <p>封装 StateGraph 的编译和执行，对外提供 Flux 流式接口。
 * 支持人工反馈中断-恢复机制：在初始化时通过 {@code interruptBefore} 预编译中断点，
 * 后续通过 {@code updateState + stream(null, resumeConfig)} 恢复执行。</p>
 */
@Slf4j
@Service
public class GraphService {

    /**
     * 原始状态图定义（用于可视化等场景）
     */
    private final StateGraph nl2sqlGraph;

    /**
     * 预编译的执行图（带 interruptBefore 中断点配置）
     */
    private final CompiledGraph compiledGraph;

    /**
     * 线程级流式上下文映射表（threadId → StreamContext）
     */
    private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

    /**
     * 构造函数：注入 StateGraph 并预编译带中断点的执行图
     *
     * @param nl2sqlGraph NL2SQL 工作流状态图
     */
    public GraphService(StateGraph nl2sqlGraph) {
        this.nl2sqlGraph = nl2sqlGraph;
        try {
            this.compiledGraph = nl2sqlGraph.compile(
                    CompileConfig.builder()
                            .interruptBefore(HUMAN_FEEDBACK_NODE)
                            .build()
            );
            log.info("工作流预编译完成，中断点: {}", HUMAN_FEEDBACK_NODE);
        } catch (GraphStateException e) {
            log.error("工作流预编译失败", e);
            throw new IllegalStateException("工作流预编译失败", e);
        }
    }

    /**
     * 执行工作流，返回 SSE 流
     *
     * <p>根据请求中是否包含人工反馈内容，自动路由到新流程或反馈恢复流程。</p>
     *
     * @param request          工作流请求体
     * @param multiTurnContext 多轮对话上下文
     * @return Flux 流式响应
     */
    public Flux<String> chat(GraphRequest request, String multiTurnContext) {
        if (StringUtils.hasText(request.getHumanFeedbackContent())) {
            return handleHumanFeedback(request, multiTurnContext);
        } else {
            return handleNewProcess(request, multiTurnContext);
        }
    }

    /**
     * 处理新的工作流执行请求
     *
     * @param request          工作流请求体
     * @param multiTurnContext 多轮对话上下文
     * @return Flux 流式响应
     */
    private Flux<String> handleNewProcess(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        boolean nl2sqlOnly = request.isNl2sqlOnly();
        boolean humanFeedback = request.isHumanFeedback();

        // 仅在非 NL2SQL-only 模式下启用人工复核
        boolean humanReviewEnabled = humanFeedback && !nl2sqlOnly;

        // 构建初始状态
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(INPUT_KEY, request.getQuery());
        initialState.put(AGENT_ID, request.getAgentId());
        initialState.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");
        initialState.put(IS_ONLY_NL2SQL, nl2sqlOnly);
        initialState.put(HUMAN_REVIEW_ENABLED, humanReviewEnabled);

        // 构建运行配置（绑定 threadId 以支持状态持久化和中断恢复）
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        log.info("开始执行工作流 - threadId: {}, input: {}, agentId: {}, nl2sqlOnly: {}, humanReviewEnabled: {}",
                threadId, request.getQuery(), request.getAgentId(), nl2sqlOnly, humanReviewEnabled);

        // 创建流式上下文
        StreamContext streamContext = new StreamContext();
        streamContextMap.put(threadId, streamContext);

        return compiledGraph.stream(initialState, config)
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
                    log.info("工作流执行完成 - threadId: {}", threadId);
                    cleanupStreamContext(threadId);
                })
                .doOnError(e -> {
                    log.error("工作流执行异常 - threadId: {}", threadId, e);
                    cleanupStreamContext(threadId);
                })
                .doOnCancel(() -> {
                    log.info("工作流被取消 - threadId: {}", threadId);
                    cleanupStreamContext(threadId);
                });
    }

    /**
     * 处理人工反馈恢复请求
     *
     * <p>将用户的反馈数据写入图状态，然后从中断点恢复执行。</p>
     *
     * @param request          工作流请求体（包含反馈内容）
     * @param multiTurnContext 多轮对话上下文
     * @return Flux 流式响应
     */
    private Flux<String> handleHumanFeedback(GraphRequest request, String multiTurnContext) {
        String threadId = request.getThreadId();
        String feedbackContent = request.getHumanFeedbackContent();

        log.info("处理人工反馈 - threadId: {}, approved: {}, feedbackContent: {}",
                threadId, !request.isRejectedPlan(), feedbackContent);

        try {
            // 构建反馈数据
            Map<String, Object> feedbackData = Map.of(
                    "feedback", !request.isRejectedPlan(),
                    "feedback_content", feedbackContent
            );

            // 构建状态更新
            Map<String, Object> stateUpdate = new HashMap<>();
            stateUpdate.put(HUMAN_FEEDBACK_DATA, feedbackData);
            stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContext != null ? multiTurnContext : "(无)");

            // 更新图状态并获取更新后的配置
            RunnableConfig baseConfig = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            RunnableConfig updatedConfig = compiledGraph.updateState(baseConfig, stateUpdate);

            // 构建恢复配置（基于更新后的配置，携带人工反馈元数据标记）
            RunnableConfig resumeConfig = RunnableConfig.builder(updatedConfig)
                    .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedbackData)
                    .build();

            // 创建流式上下文
            StreamContext streamContext = new StreamContext();
            streamContextMap.put(threadId, streamContext);

            // 从中断点恢复执行（传入 null 表示使用已有状态）
            return compiledGraph.stream(null, resumeConfig)
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
                        log.info("人工反馈恢复流程完成 - threadId: {}", threadId);
                        cleanupStreamContext(threadId);
                    })
                    .doOnError(e -> {
                        log.error("人工反馈恢复流程异常 - threadId: {}", threadId, e);
                        cleanupStreamContext(threadId);
                    })
                    .doOnCancel(() -> {
                        log.info("人工反馈恢复流程被取消 - threadId: {}", threadId);
                        cleanupStreamContext(threadId);
                    });

        } catch (Exception e) {
            log.error("人工反馈处理失败 - threadId: {}", threadId, e);
            return Flux.error(e);
        }
    }

    /**
     * 停止指定线程的流式处理
     *
     * <p>从上下文映射表中移除并清理资源，通常在客户端断连时调用。</p>
     *
     * @param threadId 线程 ID
     */
    public void stopStreamProcessing(String threadId) {
        StreamContext context = streamContextMap.remove(threadId);
        if (context != null) {
            context.cleanup();
            log.info("已停止流式处理 - threadId: {}", threadId);
        }
    }

    /**
     * 获取工作流图的 PlantUML 表示
     *
     * @return PlantUML 格式的图结构文本
     */
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

    /**
     * 清理指定线程的流式上下文
     *
     * @param threadId 线程 ID
     */
    private void cleanupStreamContext(String threadId) {
        StreamContext context = streamContextMap.remove(threadId);
        if (context != null) {
            context.cleanup();
        }
    }
}
