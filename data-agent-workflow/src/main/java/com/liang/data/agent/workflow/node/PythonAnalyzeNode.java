package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.constant.StateKey;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.PlanProcessUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.liang.data.agent.common.constant.NodeOutputKey.*;

/**
 * Python 分析节点
 *
 * <p>将 Python 执行结果写入步骤结果 memory，供 ReportGeneratorNode 使用</p>
 */
@Slf4j
@Component
public class PythonAnalyzeNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String pythonOutput = StateUtil.getStringValue(state, PYTHON_EXECUTE_NODE_OUTPUT, "{}");
        int stepNumber = PlanProcessUtil.getExecutingStepNumber(state);

        log.info("Python 分析 - 步骤: {}, 输出长度: {}", stepNumber, pythonOutput.length());

        // 将 Python 执行结果存入步骤结果
        Map<String, String> existingResults = StateUtil.getMapValue(state, SQL_RESULT_LIST_MEMORY);
        Map<String, String> updatedResults = new HashMap<>(existingResults);
        updatedResults.put("step_" + stepNumber + "_analysis", pythonOutput);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(PYTHON_ANALYSIS_NODE_OUTPUT, pythonOutput);
        resultMap.put(SQL_RESULT_LIST_MEMORY, updatedResults);

        Flux<ChatResponse> displayFlux = Flux.just(ChatResponseUtil.createResponse("Python 分析结果已记录"));

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, v -> resultMap, displayFlux);

        resultMap.put(PYTHON_ANALYSIS_NODE_OUTPUT, generator);
        return resultMap;
    }
}
