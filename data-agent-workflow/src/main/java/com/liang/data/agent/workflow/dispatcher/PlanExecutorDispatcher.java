package com.liang.data.agent.workflow.dispatcher;

import static com.liang.data.agent.workflow.constants.PlanConstants.MAX_REPAIR_COUNT;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.liang.data.agent.common.constant.ControlFlowKey.*;
import static com.liang.data.agent.common.constant.StateKey.PLANNER_NODE;

/**
 * PlanExecutor 路由: 校验通过→下一节点, 校验失败→Planner重修/END
 */
@Slf4j
public class PlanExecutorDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        boolean ok = StateUtil.getObjectValue(state, PLAN_VALIDATION_STATUS, Boolean.class, false);
        if (ok) {
            String next = state.value(PLAN_NEXT_NODE, END);
            return "END".equals(next) ? END : next;
        }

        int repair = StateUtil.getObjectValue(state, PLAN_REPAIR_COUNT, Integer.class, 0);
        if (repair > MAX_REPAIR_COUNT) {
            return END;
        }
        
        return PLANNER_NODE;
    }
}
