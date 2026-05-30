package com.liang.data.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.liang.data.agent.common.constant.ControlFlowKey.CLARIFICATION_NEXT_NODE;
import static com.liang.data.agent.common.constant.StateKey.MEMORY_CANDIDATE_NODE;

@Slf4j
public class ClarificationConfirmDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        String nextNode = StateUtil.getStringValue(state, CLARIFICATION_NEXT_NODE, MEMORY_CANDIDATE_NODE);
        log.info("澄清确认后路由到: {}", nextNode);
        return nextNode;
    }
}
