package com.liang.data.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.liang.data.agent.common.config.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.liang.data.agent.common.constant.ControlFlowKey.SQL_GENERATE_COUNT;
import static com.liang.data.agent.common.constant.NodeOutputKey.SQL_GENERATE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.SEMANTIC_CONSISTENCY_NODE;
import static com.liang.data.agent.common.constant.StateKey.SQL_GENERATE_NODE;

/**
 * SQL 生成路由: 成功→语义检查, 失败+未满重试→重新生成, 失败+满重试→END
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlGenerateDispatcher implements EdgeAction {

    private final DataAgentProperties properties;

    @Override
    public String apply(OverAllState state) {
        int maxRetryCount = properties.getMaxSqlRetryCount();
        Optional<Object> optional = state.value(SQL_GENERATE_OUTPUT);
        if (optional.isEmpty()) {
            int currentCount = state.value(SQL_GENERATE_COUNT, maxRetryCount);
            if (currentCount < maxRetryCount) {
                log.info("SQL 生成失败，开始重试。当前次数: {}", currentCount);
                return SQL_GENERATE_NODE;
            }
            log.error("SQL 生成失败，达到最大重试次数 {}", maxRetryCount);
            return END;
        }
        String sqlOutput = (String) optional.get();
        log.info("SQL 生成结果: {}", sqlOutput);
        
        if (END.equals(sqlOutput)) {
            log.info("检测到流程结束标志: {}", END);
            return END;
        }
        log.info("SQL 生成成功 → 语义一致性检查");
        return SEMANTIC_CONSISTENCY_NODE;
    }
}