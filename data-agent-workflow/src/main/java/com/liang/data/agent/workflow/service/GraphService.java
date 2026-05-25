package com.liang.data.agent.workflow.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.ModeSwitch.IS_ONLY_NL2SQL;
import static com.liang.data.agent.common.constant.StateKey.*;

/**
 * 工作流 SSE 服务
 *
 * <p>封装 StateGraph 的编译和执行，对外提供 Flux 流式接口</p>
 *
 * <p>每次调用 chat() 会创建一个新的 CompiledGraph 实例，确保线程安全</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final StateGraph nl2sqlGraph;

    /**
     * 执行工作流，返回 SSE 流
     *
     * @param input     用户输入
     * @param agentId   智能体 ID
     * @param multiTurn 多轮上下文 (可选)
     * @return Flux 流式响应
     */
    public Flux<String> chat(String input, String agentId, String multiTurn) {
        return chat(input, agentId, multiTurn, false);
    }

    /**
     * 执行工作流 (支持 NL2SQL-only 模式)
     */
    public Flux<String> chat(String input, String agentId, String multiTurn, boolean nl2sqlOnly) {
        try {
            // 每次编译新的实例 (线程安全)
            CompiledGraph compiledGraph = nl2sqlGraph.compile();

            // 构建初始 state
            Map<String, Object> initialState = Map.of(
                    INPUT_KEY, input,
                    AGENT_ID, agentId,
                    MULTI_TURN_CONTEXT, multiTurn != null ? multiTurn : "(无)",
                    IS_ONLY_NL2SQL, nl2sqlOnly);

            log.info("开始执行工作流 - input: {}, agentId: {}, nl2sqlOnly: {}", input, agentId, nl2sqlOnly);

            // 流式执行
            return compiledGraph.stream(initialState)
                    .map(response -> {
                        // 需通过模式匹配判断类型是否为 StreamingOutput
                        if (response instanceof StreamingOutput<?> so) {
                            String chunk = so.chunk();
                            return chunk != null ? chunk : "";
                        }
                        return "";
                    })
                    .filter(s -> !s.isEmpty())
                    .doOnComplete(() -> log.info("工作流执行完成"))
                    .doOnError(e -> log.error("工作流执行异常", e));

        } catch (Exception e) {
            log.error("工作流编译失败", e);
            return Flux.error(e);
        }
    }

    /**
     * 获取工作流图的 PlantUML 表示
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
}
