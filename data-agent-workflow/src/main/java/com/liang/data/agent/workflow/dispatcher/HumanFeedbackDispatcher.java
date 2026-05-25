package com.liang.data.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;

import static com.liang.data.agent.common.constant.ControlFlowKey.HUMAN_NEXT_NODE;
import static com.liang.data.agent.common.constant.ControlFlowKey.WAIT_FOR_FEEDBACK;

/**
 * 人工反馈路由: WAIT→END(暂停), 其他→下一节点
 */
public class HumanFeedbackDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) throws Exception {
        String next = state.value(HUMAN_NEXT_NODE, StateGraph.END);
        return WAIT_FOR_FEEDBACK.equals(next) ? StateGraph.END : next;
    }
}
