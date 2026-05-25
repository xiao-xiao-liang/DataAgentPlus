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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.PLAN_CURRENT_STEP;
import static com.liang.data.agent.common.constant.NodeOutputKey.SQL_EXECUTE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.RESULT;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGeneratorNode implements NodeAction {

    private final LlmService llmService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String userInput = StateUtil.getCanonicalQuery(state);
        int currentStep = StateUtil.getObjectValue(state, PLAN_CURRENT_STEP, Integer.class, 1);
        Plan plan = PlanProcessUtil.getPlan(state);
        ExecutionStep step = PlanProcessUtil.getCurrentExecutionStep(plan, currentStep);
        String summary = step.getToolParameters() != null ? step.getToolParameters().getSummaryAndRecommendations() : "";
        
        Map<String, String> results = StateUtil.getMapValue(state, SQL_EXECUTE_NODE_OUTPUT);
        
        String reportPrompt = PromptHelper.buildReportGeneratorPrompt(userInput, results.toString(), summary);
        Flux<ChatResponse> reportFlux = llmService.callUser(reportPrompt);
        
        TextType reportType = TextType.MARK_DOWN;
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, "开始生成报告...", "报告生成完成！",
                content -> {
                    Map<String, Object> r = new HashMap<>();
                    r.put(RESULT, content);
                    return r;
                },
                Flux.concat(
                        Flux.just(ChatResponseUtil.createPureResponse(reportType.getStartSign())), reportFlux,
                        Flux.just(ChatResponseUtil.createPureResponse(reportType.getEndSign()))
                )
        );
        return Map.of(RESULT, generator);
    }
}