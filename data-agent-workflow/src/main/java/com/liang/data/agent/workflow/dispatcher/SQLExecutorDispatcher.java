package com.liang.data.agent.workflow.dispatcher;

import com.liang.data.agent.common.constant.StateKey;
import com.liang.data.agent.workflow.dto.SqlRetryDTO;
import com.liang.data.agent.workflow.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;

import static com.liang.data.agent.common.constant.ControlFlowKey.SQL_REGENERATE_REASON;

/**
 * SQL 执行路由: 执行失败→SqlGenerate, 执行成功→PlanExecutor
 */
@Slf4j
public class SQLExecutorDispatcher implements EdgeAction {
    
    @Override
    public String apply(OverAllState state) {
        SqlRetryDTO retryDto = StateUtil.getObjectValue(state, SQL_REGENERATE_REASON, SqlRetryDTO.class);
        
        if (retryDto.sqlExecuteFail()) {
            log.warn("SQL 执行失败 → 重新生成");
            return StateKey.SQL_GENERATE_NODE;
        }
        
        log.info("SQL 执行成功 → PlanExecutor");
        return StateKey.PLAN_EXECUTOR_NODE;
    }
}
