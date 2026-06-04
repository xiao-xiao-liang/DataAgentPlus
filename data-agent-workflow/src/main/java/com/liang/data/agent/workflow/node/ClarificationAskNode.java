package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.constant.SseEventKey;
import com.liang.data.agent.workflow.dto.node.ClarificationRequestDTO;
import com.liang.data.agent.workflow.dto.node.FeasibilityAssessmentOutputDTO;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import com.liang.data.agent.workflow.util.WorkflowEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.CLARIFICATION_NEXT_NODE;
import static com.liang.data.agent.common.constant.ControlFlowKey.MEMORY_CANDIDATE_NEXT_NODE;
import static com.liang.data.agent.common.constant.ControlFlowKey.WAIT_FOR_CLARIFICATION;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_REQUEST;
import static com.liang.data.agent.common.constant.NodeOutputKey.FEASIBILITY_ASSESSMENT_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static com.liang.data.agent.common.constant.StateKey.MEMORY_CANDIDATE_NODE;
import static com.liang.data.agent.common.constant.StateKey.PLANNER_NODE;
import static com.liang.data.agent.common.constant.StateKey.SCHEMA_RECALL_NODE;
import static com.liang.data.agent.common.constant.StateKey.THREAD_ID;

@Slf4j
@Component
public class ClarificationAskNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) {
        FeasibilityAssessmentOutputDTO feasibility = StateUtil.getObjectValueOrNull(
                state,
                FEASIBILITY_ASSESSMENT_NODE_OUTPUT,
                FeasibilityAssessmentOutputDTO.class
        );

        ClarificationRequestDTO request = new ClarificationRequestDTO();
        String continueNode = PLANNER_NODE;
        if (feasibility != null) {
            request.setReason(feasibility.getReason());
            request.setMissingTerm(feasibility.getMissingTerm());
            request.setQuestion(feasibility.getClarificationQuestion());
            request.setSuggestedMemoryType(feasibility.getSuggestedMemoryType());
            request.setMemoryWorthSaving(Boolean.TRUE.equals(feasibility.getMemoryWorthSaving()));
            request.setAffectsSchemaRecall(Boolean.TRUE.equals(feasibility.getAffectsSchemaRecall()));
            if (Boolean.TRUE.equals(feasibility.getAffectsSchemaRecall())) {
                continueNode = SCHEMA_RECALL_NODE;
            }
        } else {
            QueryEnhanceOutputDTO query = StateUtil.getObjectValueOrNull(
                    state,
                    QUERY_ENHANCE_NODE_OUTPUT,
                    QueryEnhanceOutputDTO.class
            );
            String canonicalQuery = query == null ? "" : query.getCanonicalQuery();
            request.setReason("当前问题没有召回到可用的数据表，需要补充业务对象、系统模块、表/字段线索或指标口径后重新检索 Schema。");
            request.setMissingTerm(canonicalQuery);
            request.setQuestion("我没有在当前数据源中检索到能支撑「" + canonicalQuery + "」的数据表。请补充一下它对应的业务对象、系统模块、表名/字段名线索，或说明相关指标口径。");
            request.setSuggestedMemoryType("BUSINESS_KNOWLEDGE");
            request.setMemoryWorthSaving(true);
            request.setAffectsSchemaRecall(true);
            continueNode = SCHEMA_RECALL_NODE;
        }

        String question = request.getQuestion();
        if (question == null || question.isBlank()) {
            question = "这个问题缺少关键业务口径，请补充指标定义、筛选条件或统计口径后我再继续分析。";
            request.setQuestion(question);
        }

        String agentId = StateUtil.getStringValue(state, AGENT_ID, "");
        String threadId = StateUtil.getStringValue(state, THREAD_ID, "");
        String event = WorkflowEventUtil.encode(agentId, threadId, SseEventKey.CLARIFICATION_REQUEST, request);
        Flux<ChatResponse> sourceFlux = Flux.just(
                ChatResponseUtil.createPureResponse(event),
                ChatResponseUtil.createResponse(question)
        );

        String routeAfterConfirm = Boolean.TRUE.equals(request.getMemoryWorthSaving())
                ? MEMORY_CANDIDATE_NODE
                : continueNode;
        String routeAfterMemoryCandidate = continueNode;
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(),
                state,
                llmOutput -> Map.of(
                        CLARIFICATION_REQUEST, request,
                        CLARIFICATION_NEXT_NODE, routeAfterConfirm,
                        MEMORY_CANDIDATE_NEXT_NODE, routeAfterMemoryCandidate,
                        WAIT_FOR_CLARIFICATION, true
                ),
                sourceFlux
        );

        log.info("澄清提问: {}", question);
        return Map.of(CLARIFICATION_REQUEST, generator);
    }
}
