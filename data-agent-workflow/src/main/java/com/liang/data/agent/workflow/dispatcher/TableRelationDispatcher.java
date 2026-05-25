package com.liang.data.agent.workflow.dispatcher;

import com.liang.data.agent.common.constant.StateKey;
import com.liang.data.agent.workflow.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.liang.data.agent.common.constant.ControlFlowKey.TABLE_RELATION_RETRY_COUNT;
import static com.liang.data.agent.common.constant.NodeOutputKey.TABLE_RELATION_EXCEPTION_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.TABLE_RELATION_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.FEASIBILITY_ASSESSMENT_NODE;
import static com.liang.data.agent.common.constant.StateKey.TABLE_RELATION_NODE;

/**
 * 表关系推理路由: 有结果→Feasibility, 有可重试错误→重试, 否则→END
 */
public class TableRelationDispatcher implements EdgeAction {

    private static final int MAX_RETRY_COUNT = 3;

    @Override
    public String apply(OverAllState state) throws Exception {
        String errorFlag = StateUtil.getStringValue(state, TABLE_RELATION_EXCEPTION_OUTPUT, null);
        Integer retryCount = StateUtil.getObjectValue(state, TABLE_RELATION_RETRY_COUNT, Integer.class, 0);

        if (errorFlag != null && !errorFlag.isEmpty()) {
            if (errorFlag.startsWith("RETRYABLE:") && retryCount < MAX_RETRY_COUNT) {
                return TABLE_RELATION_NODE;
            }
            return END;
        }
        
        if (state.value(TABLE_RELATION_OUTPUT).isPresent()) {
            return FEASIBILITY_ASSESSMENT_NODE;
        }
        return END;
    }
}
