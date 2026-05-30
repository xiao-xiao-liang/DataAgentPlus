package com.liang.data.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.liang.data.agent.common.constant.ControlFlowKey.MEMORY_CANDIDATE_NEXT_NODE;
import static com.liang.data.agent.common.constant.StateKey.PLANNER_NODE;

@Slf4j
public class MemoryCandidateDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        String nextNode = StateUtil.getStringValue(state, MEMORY_CANDIDATE_NEXT_NODE, PLANNER_NODE);
        log.info("候选知识处理后路由到: {}", nextNode);
        return nextNode;
    }
}
