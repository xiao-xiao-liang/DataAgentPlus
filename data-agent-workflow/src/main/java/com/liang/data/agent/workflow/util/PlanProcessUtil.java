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
     *
     * <p>注意：PlanExecutorNode 在分发到工具节点前会先自增 PLAN_CURRENT_STEP，
     * 因此工具节点内应使用 {@link #getExecutingStepNumber(OverAllState)} 获取实际正在执行的步骤号。</p>
     */
    public static int getCurrentStepNumber(OverAllState state) {
        return state.value(PLAN_CURRENT_STEP, 1);
    }

    /**
     * 获取实际正在执行的步骤编号
     *
     * <p>由于 PlanExecutorNode 在分发前已将 PLAN_CURRENT_STEP 自增 1，
     * 此方法返回 currentStep - 1 以得到实际正在执行的步骤编号。</p>
     */
    public static int getExecutingStepNumber(OverAllState state) {
        return Math.max(getCurrentStepNumber(state) - 1, 1);
    }

    /**
     * 从 state 中获取实际正在执行的 ExecutionStep（工具节点专用）
     *
     * <p>与 {@link #getCurrentExecutionStep(OverAllState)} 不同，此方法考虑了
     * PlanExecutorNode 预自增的情况，返回正确的当前执行步骤。</p>
     */
    public static ExecutionStep getExecutingStep(OverAllState state) {
        Plan plan = getPlan(state);
        int executingStep = getExecutingStepNumber(state);
        return getCurrentExecutionStep(plan, executingStep);
    }

    /**
     * 获取实际正在执行步骤的 instruction 描述（工具节点专用）
     */
    public static String getExecutingStepInstruction(OverAllState state) {
        ExecutionStep.ToolParameters params = getExecutingStep(state).getToolParameters();
        return params != null ? params.getInstruction() : "无";
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
