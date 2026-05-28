package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.workflow.dto.planner.ExecutionStep;
import com.liang.data.agent.workflow.dto.planner.Plan;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.PlanProcessUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.PLAN_CURRENT_STEP;
import static com.liang.data.agent.common.constant.NodeOutputKey.PLANNER_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.SQL_EXECUTE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.RESULT;

/**
 * 报告生成节点
 *
 * <p>基于之前各步骤收集到的物理执行结果与分析建议，
 * 调用大模型生成完整的、格式化的 Markdown 总结报告，并清理工作流执行中的冗余中间状态。</p>
 */
@Slf4j
@Component
public class ReportGeneratorNode implements NodeAction {

    private final LlmService llmService;
    private final BeanOutputConverter<Plan> converter;

    public ReportGeneratorNode(LlmService llmService) {
        this.llmService = llmService;
        this.converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {});
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("开始执行报告生成节点...");

        // 1. 获取中间状态和配置
        String plannerNodeOutput = StateUtil.getStringValue(state, PLANNER_NODE_OUTPUT);
        String userInput = StateUtil.getCanonicalQuery(state);
        int currentStep = PlanProcessUtil.getExecutingStepNumber(state);
        
        Map<String, String> executionResults = StateUtil.getMapValue(state, SQL_EXECUTE_NODE_OUTPUT);

        // 2. 解析 Plan 并防御性提取步骤 Summary
        Plan plan;
        try {
            plan = converter.convert(plannerNodeOutput);
        } catch (Exception e) {
            log.error("解析 Planner 执行计划失败，旧计划内容: {}", plannerNodeOutput, e);
            plan = new Plan();
        }

        String summaryAndRecommendations = "";
        if (plan.getExecutionPlan() != null) {
            int stepIndex = currentStep - 1;
            if (stepIndex >= 0 && stepIndex < plan.getExecutionPlan().size()) {
                ExecutionStep executionStep = plan.getExecutionPlan().get(stepIndex);
                if (executionStep != null && executionStep.getToolParameters() != null) {
                    summaryAndRecommendations = executionStep.getToolParameters().getSummaryAndRecommendations();
                }
            } else {
                log.warn("步骤索引越界: {}/{}", stepIndex, plan.getExecutionPlan().size());
            }
        }

        // 3. 构建结构化上下文描述
        String userRequirementsAndPlan = PromptHelper.buildUserRequirementsAndPlan(userInput, plan);
        String analysisStepsAndData = PromptHelper.buildAnalysisStepsAndData(plan, executionResults);

        // 4. 生成报告
        String reportPrompt = PromptHelper.buildReportGeneratorPrompt(
                userRequirementsAndPlan, analysisStepsAndData, summaryAndRecommendations);
        
        Flux<ChatResponse> reportFlux = llmService.callUser(reportPrompt);
        TextType reportType = TextType.MARK_DOWN;

        // 5. 组合流式响应并设置状态清理
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(),
                state,
                "开始整理并生成完整分析报告...\n",
                "\n[系统] 分析报告生成完毕！",
                reportContent -> {
                    log.info("报告内容生成结束，大小: {} 字符，触发中间状态清理", reportContent.length());
                    Map<String, Object> finalResult = new HashMap<>();
                    finalResult.put(RESULT, reportContent);
                    // 清理冗余的状态，防止污染下一轮对话
                    finalResult.put(SQL_EXECUTE_NODE_OUTPUT, null);
                    finalResult.put(PLAN_CURRENT_STEP, null);
                    finalResult.put(PLANNER_NODE_OUTPUT, null);
                    return finalResult;
                },
                Flux.concat(
                        Flux.just(ChatResponseUtil.createPureResponse(reportType.getStartSign())),
                        reportFlux,
                        Flux.just(ChatResponseUtil.createPureResponse(reportType.getEndSign()))
                )
        );

        return Map.of(RESULT, generator);
    }
}
