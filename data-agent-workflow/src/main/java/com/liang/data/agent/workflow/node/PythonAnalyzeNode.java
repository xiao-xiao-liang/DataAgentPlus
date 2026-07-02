package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.gateway.constants.ModelGatewayConstant;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.PlanProcessUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.*;
import static com.liang.data.agent.common.constant.NodeOutputKey.*;

/**
 * Python 分析节点
 *
 * <p>利用大模型根据 Python 沙箱代码运行得出的结果进行结构化智能总结与分析，
 * 并将分析结果写入执行结果 memory，同时递增当前步骤计数。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonAnalyzeNode implements NodeAction {

    private final LlmService llmService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 获取上下文及执行结果
        String userQuery = StateUtil.getCanonicalQuery(state);
        String pythonOutput = StateUtil.getStringValue(state, PYTHON_EXECUTE_NODE_OUTPUT, "{}");
        int currentStep = PlanProcessUtil.getExecutingStepNumber(state);
        
        Map<String, String> sqlExecuteResult = StateUtil.getMapValue(state, SQL_EXECUTE_NODE_OUTPUT);

        // 2. 检查是否进入降级模式
        boolean isFallbackMode = StateUtil.getObjectValue(state, PYTHON_FALLBACK_MODE, Boolean.class, false);

        if (isFallbackMode) {
            String fallbackMessage = "Python 脚本执行完毕，但在进行高级分析时出现异常或已降级。原始输出为: " + pythonOutput;
            log.warn("Python分析节点检测到降级模式，步骤: {}，返回固定提示信息", currentStep);

            Map<String, Object> resultMap = new HashMap<>();
            Map<String, String> updatedSqlResult = new HashMap<>(sqlExecuteResult);
            updatedSqlResult.put("step_" + currentStep + "_analysis", fallbackMessage);
            resultMap.put(SQL_EXECUTE_NODE_OUTPUT, updatedSqlResult);
            resultMap.put(PLAN_CURRENT_STEP, currentStep + 1);

            Flux<ChatResponse> fallbackFlux = Flux.just(ChatResponseUtil.createResponse(fallbackMessage));

            Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> resultMap, fallbackFlux);

            return Map.of(PYTHON_ANALYSIS_NODE_OUTPUT, generator);
        }

        log.info("开始对 Python 运行结果进行大模型总结分析，步骤: {}", currentStep);

        // 3. 渲染分析 Prompt 并调用大模型
        String systemPrompt = PromptHelper.buildPythonAnalyzePrompt(userQuery, pythonOutput);
        Flux<ChatResponse> pythonAnalyzeFlux = llmService.callSystem(ModelGatewayConstant.PYTHON_ANALYZE, systemPrompt);

        // 4. 定义更新的状态映射
        Map<String, Object> finalResultMap = new HashMap<>();

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                pythonAnalyzeFlux,
                Flux.just(ChatResponseUtil.createResponse("正在智能分析 Python 运行结果...\n")),
                Flux.just(ChatResponseUtil.createResponse("\n[系统] Python 运行结果分析完成。")),
                aiResponse -> {
                    Map<String, String> updatedSqlResult = new HashMap<>(sqlExecuteResult);
                    updatedSqlResult.put("step_" + currentStep + "_analysis", aiResponse.trim());
                    log.info("Python 分析完成，步骤: {}，结果长度: {}", currentStep, aiResponse.trim().length());
                    
                    finalResultMap.put(SQL_EXECUTE_NODE_OUTPUT, updatedSqlResult);
                    finalResultMap.put(PLAN_CURRENT_STEP, currentStep + 1);
                    return finalResultMap;
                }
        );

        return Map.of(PYTHON_ANALYSIS_NODE_OUTPUT, generator);
    }
}
