package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.constant.StateKey;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.PYTHON_IS_SUCCESS;
import static com.liang.data.agent.common.constant.ControlFlowKey.PYTHON_TRIES_COUNT;
import static com.liang.data.agent.common.constant.NodeOutputKey.PYTHON_EXECUTE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.PYTHON_GENERATE_NODE_OUTPUT;

/**
 * Python 执行节点 (Stub 桩实现)
 *
 * <p>暂用 Stub 实现。真实的 Python 执行引擎后面通过 GraalVM Polyglot 或 Docker 沙箱实现。</p>
 *
 * <p>当前行为: 直接返回 "{}" 作为执行结果，标记为成功</p>
 */
@Slf4j
@Component
public class PythonExecuteNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String pythonCode = StateUtil.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
        int triesCount = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);

        log.info("Python 执行 (Stub) - 代码长度: {} 字符, 尝试次数: {}", pythonCode.length(), triesCount);

        // Stub: 直接返回空 JSON 作为执行结果
        String stubResult = "{}";

        Flux<ChatResponse> displayFlux = Flux.just(
                ChatResponseUtil.createResponse("开始执行Python代码... (Stub模式)"),
                ChatResponseUtil.createResponse("标准输出："),
                ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
                ChatResponseUtil.createResponse(stubResult),
                ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
                ChatResponseUtil.createResponse("Python代码执行完成 (Stub)！"));

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state,
                v -> Map.of(PYTHON_EXECUTE_NODE_OUTPUT, stubResult, PYTHON_IS_SUCCESS, true),
                displayFlux);

        return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
    }
}
