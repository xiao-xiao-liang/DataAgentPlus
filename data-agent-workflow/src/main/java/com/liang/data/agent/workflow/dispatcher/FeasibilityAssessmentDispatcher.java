package com.liang.data.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.liang.data.agent.common.enums.FeasibilityRequestType;
import com.liang.data.agent.workflow.dto.node.FeasibilityAssessmentOutputDTO;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.liang.data.agent.common.constant.NodeOutputKey.FEASIBILITY_ASSESSMENT_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.CLARIFICATION_ASK_NODE;
import static com.liang.data.agent.common.constant.StateKey.PLANNER_NODE;

@Slf4j
public class FeasibilityAssessmentDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        FeasibilityAssessmentOutputDTO output = StateUtil.getObjectValue(
                state,
                FEASIBILITY_ASSESSMENT_NODE_OUTPUT,
                FeasibilityAssessmentOutputDTO.class,
                new FeasibilityAssessmentOutputDTO()
        );
        String requestType = output.getRequestType();
        if (FeasibilityRequestType.DATA_ANALYSIS.getCode().equals(requestType)) {
            log.info("可行性评估: DATA_ANALYSIS -> Planner");
            return PLANNER_NODE;
        }
        if (FeasibilityRequestType.NEED_CLARIFICATION.getCode().equals(requestType)) {
            log.info("可行性评估: NEED_CLARIFICATION -> ClarificationAsk");
            return CLARIFICATION_ASK_NODE;
        }
        log.info("可行性评估: CHAT/OTHER -> END, type={}", requestType);
        return END;
    }
}
