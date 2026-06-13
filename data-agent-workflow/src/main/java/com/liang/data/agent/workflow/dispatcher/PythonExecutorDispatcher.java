package com.liang.data.agent.workflow.dispatcher;

import static com.liang.data.agent.workflow.constants.PythonExecutionConstants.MAX_TRIES;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.liang.data.agent.common.constant.ControlFlowKey.*;
import static com.liang.data.agent.common.constant.StateKey.PYTHON_ANALYZE_NODE;
import static com.liang.data.agent.common.constant.StateKey.PYTHON_GENERATE_NODE;

/**
 * Python 执行路由: 降级→分析, 成功→分析, 失败+未满重试→重新生成, 失败+满→END
 */
@Slf4j
public class PythonExecutorDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) throws Exception {
        boolean fallback = StateUtil.getObjectValue(state, PYTHON_FALLBACK_MODE, Boolean.class, false);
        if (fallback) {
            return PYTHON_ANALYZE_NODE;
        }

        boolean ok = StateUtil.getObjectValue(state, PYTHON_IS_SUCCESS, Boolean.class, false);
        if (!ok) {
            int tries = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);
            if (tries >= MAX_TRIES) {
                return END;
            }
            return PYTHON_GENERATE_NODE;
        }

        return PYTHON_ANALYZE_NODE;
    }
}
