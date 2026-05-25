package com.liang.data.agent.workflow.util;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.workflow.dto.planner.ExecutionStep;
import com.liang.data.agent.workflow.dto.planner.Plan;
import lombok.NoArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.PLAN_CURRENT_STEP;
import static com.liang.data.agent.common.constant.NodeOutputKey.PLANNER_NODE_OUTPUT;

/**
 * 执行计划处理工具类
 *
 * <p>从 state 中解析 Plan JSON、获取当前步骤、记录步骤结果</p>
 */
@NoArgsConstructor
public final class PlanProcessUtil {

    private static final String STEP_PREFIX = "step_";

    private static final BeanOutputConverter<Plan> CONVERTER;

    static {
        CONVERTER = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });
    }

    /**
     * 从 state 中获取当前执行步骤
     */
    public static ExecutionStep getCurrentExecutionStep(OverAllState state) {
        Plan plan = getPlan(state);
        int currentStep = getCurrentStepNumber(state);
        return getCurrentExecutionStep(plan, currentStep);
    }

    /**
     * 从 state 中解析 Plan 对象
     */
    public static Plan getPlan(OverAllState state) {
        String plannerNodeOutput = (String) state.value(PLANNER_NODE_OUTPUT)
                .orElseThrow(() -> new ServiceException("计划节点输出为空"));
        return CONVERTER.convert(plannerNodeOutput);
    }

    /**
     * 获取当前步骤编号 (默认为 1)
     */
    public static int getCurrentStepNumber(OverAllState state) {
        return state.value(PLAN_CURRENT_STEP, 1);
    }

    /**
     * 从 Plan 对象中获取指定步骤
     */
    public static ExecutionStep getCurrentExecutionStep(Plan plan, Integer currentStep) {
        List<ExecutionStep> executionPlan = plan.getExecutionPlan();
        if (CollectionUtils.isEmpty(executionPlan)) {
            throw new ServiceException("执行计划为空");
        }

        int stepIndex = currentStep - 1;
        if (stepIndex < 0 || stepIndex >= executionPlan.size()) {
            throw new ServiceException("当前步骤索引超出范围: " + stepIndex);
        }

        return executionPlan.get(stepIndex);
    }

    /**
     * 获取当前步骤的 instruction 描述
     */
    public static String getCurrentExecutionStepInstruction(OverAllState state) {
        ExecutionStep.ToolParameters currentStepParams = getCurrentExecutionStep(state).getToolParameters();
        return currentStepParams != null ? currentStepParams.getInstruction() : "无";
    }

    /**
     * 记录步骤执行结果
     */
    public static Map<String, String> addStepResult(Map<String, String> existingResults, Integer stepNumber, String result) {
        Map<String, String> updatedResults = new HashMap<>(existingResults);
        updatedResults.put(STEP_PREFIX + stepNumber, result);
        return updatedResults;
    }
}
