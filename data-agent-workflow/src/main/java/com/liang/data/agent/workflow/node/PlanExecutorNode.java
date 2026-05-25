package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.workflow.dto.planner.ExecutionStep;
import com.liang.data.agent.workflow.dto.planner.Plan;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.PlanProcessUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.liang.data.agent.common.constant.ControlFlowKey.*;
import static com.liang.data.agent.common.constant.ModeSwitch.HUMAN_REVIEW_ENABLED;
import static com.liang.data.agent.common.constant.ModeSwitch.IS_ONLY_NL2SQL;
import static com.liang.data.agent.common.constant.StateKey.*;

/**
 * PlanExecutor 节点 — 计划步骤分发器
 *
 * <p>读取当前步骤编号，决定下一个要执行的节点 (sql_generate / python_generate / report_generator)</p>
 * <p>每执行完一个步骤，步骤编号 +1，直到所有步骤完成</p>
 */
@Slf4j
@Component
public class PlanExecutorNode implements NodeAction {

    // 支持的分发节点集合
    private static final Set<String> SUPPORTED_NODES = Set.of(
            SQL_GENERATE_NODE,
            PYTHON_GENERATE_NODE,
            REPORT_GENERATOR_NODE
    );

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 计划校验阶段
        Plan plan;
        try {
            plan = PlanProcessUtil.getPlan(state);
        } catch (Exception e) {
            log.error("执行计划解析失败", e);
            return handleValidationFailure(state, "执行计划解析失败，非法的 JSON 结构。错误信息: " + e.getMessage());
        }

        // 校验计划的整体结构
        if (!validateExecutionPlanStructure(plan)) {
            log.error("执行计划校验失败: 计划为空或不包含任何执行步骤");
            return handleValidationFailure(state, "执行计划校验失败: 生成的计划为空或无有效步骤。");
        }

        // 逐个校验执行步骤
        for (ExecutionStep step : plan.getExecutionPlan()) {
            String stepValidationError = validateExecutionStep(step);
            if (stepValidationError != null) {
                log.error("步骤 {} 校验失败: {}", step.getStep(), stepValidationError);
                return handleValidationFailure(state, stepValidationError);
            }
        }

        log.info("执行计划校验成功。");

        // 2. 人工复核校验
        boolean humanReviewEnabled = StateUtil.getObjectValue(state, HUMAN_REVIEW_ENABLED, Boolean.class, false);
        if (humanReviewEnabled) {
            log.info("已开启人工复核：流转至 human_feedback 节点");
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put(PLAN_VALIDATION_STATUS, true);
            resultMap.put(PLAN_NEXT_NODE, HUMAN_FEEDBACK_NODE);

            Flux<ChatResponse> displayFlux = Flux.just(
                    ChatResponseUtil.createResponse("执行计划校验通过，已开启人工复核，正在跳转至人工反馈节点...")
            );
            Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> resultMap, displayFlux
            );

            return Map.of(PLAN_EXECUTOR_NODE, generator);
        }

        // 3. 执行分发逻辑
        int currentStep = PlanProcessUtil.getCurrentStepNumber(state);
        List<ExecutionStep> executionPlan = plan.getExecutionPlan();
        Boolean isOnlyNl2Sql = StateUtil.getObjectValue(state, IS_ONLY_NL2SQL, Boolean.class, false);

        // 如果当前步骤超出总步骤数，表示计划执行完毕
        if (currentStep > executionPlan.size()) {
            log.info("所有执行步骤已完成。总步骤数: {}", executionPlan.size());
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put(PLAN_CURRENT_STEP, 1);
            resultMap.put(PLAN_NEXT_NODE, isOnlyNl2Sql ? END : REPORT_GENERATOR_NODE);
            resultMap.put(PLAN_VALIDATION_STATUS, true);

            Flux<ChatResponse> displayFlux = Flux.just(
                    ChatResponseUtil.createResponse("所有计划步骤已执行完成！即将生成最终报告。")
            );

            Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> resultMap, displayFlux
            );

            return Map.of(PLAN_EXECUTOR_NODE, generator);
        }

        // 获取当前执行的步骤并进行分发
        ExecutionStep curStep = PlanProcessUtil.getCurrentExecutionStep(plan, currentStep);
        String toolToUse = curStep.getToolToUse();
        log.info("开始执行步骤 {}/{}, 使用工具: {}", currentStep, executionPlan.size(), toolToUse);

        // 构建分发结果
        Map<String, Object> resultMap = determineNextNode(toolToUse);
        if (Boolean.TRUE.equals(resultMap.get(PLAN_VALIDATION_STATUS))) {
            // 校验通过，步骤自增
            resultMap.put(PLAN_CURRENT_STEP, currentStep + 1);

            Flux<ChatResponse> displayFlux = Flux.just(
                    ChatResponseUtil.createResponse(String.format("即将执行步骤 %d/%d: [%s] -> %s",
                            currentStep, executionPlan.size(), toolToUse,
                            curStep.getToolParameters() != null ? curStep.getToolParameters().getInstruction() : ""))
            );

            Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> resultMap, displayFlux
            );

            return Map.of(PLAN_EXECUTOR_NODE, generator);
        } else {
            // 兜底逻辑：如果存在不支持的节点类型，直接报错重修
            String errorMsg = (String) resultMap.get(PLAN_VALIDATION_ERROR);
            return handleValidationFailure(state, errorMsg);
        }
    }

    /**
     * 处理计划校验失败的情况
     */
    private Map<String, Object> handleValidationFailure(OverAllState state, String errorMessage) {
        int repairCount = StateUtil.getObjectValue(state, PLAN_REPAIR_COUNT, Integer.class, 0);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(PLAN_VALIDATION_STATUS, false);
        resultMap.put(PLAN_VALIDATION_ERROR, errorMessage);
        resultMap.put(PLAN_REPAIR_COUNT, repairCount + 1);

        Flux<ChatResponse> displayFlux = Flux.just(
                ChatResponseUtil.createResponse("计划校验失败：" + errorMessage + " (当前重试修复次数: " + (repairCount + 1) + ")")
        );
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, v -> resultMap, displayFlux
        );

        return Map.of(PLAN_EXECUTOR_NODE, generator);
    }

    /**
     * 校验计划的整体结构是否合法
     */
    private boolean validateExecutionPlanStructure(Plan plan) {
        return plan != null && plan.getExecutionPlan() != null && !plan.getExecutionPlan().isEmpty();
    }

    /**
     * 校验单个具体步骤的参数
     *
     * @return 错误信息，如果校验通过则返回 null
     */
    private String validateExecutionStep(ExecutionStep step) {
        String toolToUse = step.getToolToUse();
        if (toolToUse == null || (!SUPPORTED_NODES.contains(toolToUse) && !HUMAN_FEEDBACK_NODE.equals(toolToUse))) {
            return "步骤 " + step.getStep() + " 校验失败: 不支持的工具类型 '" + toolToUse + "'";
        }

        if (step.getToolParameters() == null) {
            return "步骤 " + step.getStep() + " 校验失败: 缺失必要参数 'tool_parameters'";
        }

        return switch (toolToUse) {
            case SQL_GENERATE_NODE -> !StringUtils.hasText(step.getToolParameters().getInstruction())
                    ? "步骤 " + step.getStep() + " (sql_generate) 校验失败: 缺失 instruction 参数。" : null;

            case PYTHON_GENERATE_NODE -> !StringUtils.hasText(step.getToolParameters().getInstruction())
                    ? "步骤 " + step.getStep() + " (python_generate) 校验失败: 缺失 instruction 参数。" : null;

            case REPORT_GENERATOR_NODE -> !StringUtils.hasText(step.getToolParameters().getSummaryAndRecommendations())
                    ? "步骤 " + step.getStep() + " (report_generator) 校验失败: 缺失 summary_and_recommendations 参数。" : null;

            default -> null;
        };
    }

    /**
     * 决定下一步执行的节点
     */
    private Map<String, Object> determineNextNode(String toolToUse) {
        Map<String, Object> results = new HashMap<>();
        if (SUPPORTED_NODES.contains(toolToUse) || HUMAN_FEEDBACK_NODE.equals(toolToUse)) {
            log.info("决定下一个执行节点为: {}", toolToUse);
            results.put(PLAN_NEXT_NODE, toolToUse);
            results.put(PLAN_VALIDATION_STATUS, true);
        } else {
            results.put(PLAN_VALIDATION_STATUS, false);
            results.put(PLAN_VALIDATION_ERROR, "无法确定执行路径: 不支持的工具节点 " + toolToUse);
        }
        return results;
    }
}