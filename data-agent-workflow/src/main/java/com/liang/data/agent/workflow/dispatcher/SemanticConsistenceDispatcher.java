package com.liang.data.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;

import static com.liang.data.agent.common.constant.NodeOutputKey.SEMANTIC_CONSISTENCY_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.SQL_EXECUTE_NODE;
import static com.liang.data.agent.common.constant.StateKey.SQL_GENERATE_NODE;

/** 语义一致性路由: 通过→SqlExecute, 不通过→SqlGenerate */
@Slf4j
public class SemanticConsistenceDispatcher implements EdgeAction {
    @Override
    public String apply(OverAllState state) {
        Boolean validate = (Boolean) state.value(SEMANTIC_CONSISTENCY_NODE_OUTPUT).orElse(false);
        if (validate) {
            log.info("语义一致性通过 → SQL 执行");
            return SQL_EXECUTE_NODE;
        }
        
        log.info("语义一致性不通过 → SQL 重新生成");
        return SQL_GENERATE_NODE;
    }
}
