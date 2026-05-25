package com.liang.data.agent.workflow.dispatcher;

import com.liang.data.agent.common.constant.StateKey;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.liang.data.agent.common.constant.NodeOutputKey.FEASIBILITY_ASSESSMENT_NODE_OUTPUT;

/** 可行性评估路由: 含"数据分析"→Planner, 否则→END */
@Slf4j
public class FeasibilityAssessmentDispatcher implements EdgeAction {
    
    @Override
    public String apply(OverAllState state) throws Exception {
        String value = state.value(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, END);
        
        if (value != null && value.contains("【需求类型】：《数据分析》")) {
            log.info("可行性评估: 数据分析 → Planner");
            return StateKey.PLANNER_NODE;
        }
        
        log.info("可行性评估: 非数据分析 → END");
        return END;
    }
}
