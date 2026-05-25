package com.liang.data.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.liang.data.agent.common.constant.StateKey;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;

/**
 * 查询增强路由
 *
 * <p>canonicalQuery 和 expandedQueries 都有效 → SchemaRecallNode, 否则 → END</p>
 */
@Slf4j
public class QueryEnhanceDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) throws Exception {
        QueryEnhanceOutputDTO dto = StateUtil.getObjectValue(
                state, QUERY_ENHANCE_NODE_OUTPUT, QueryEnhanceOutputDTO.class);

        boolean isCanonicalEmpty = dto.getCanonicalQuery() == null
                || dto.getCanonicalQuery().trim().isEmpty();
        boolean isExpandedEmpty = dto.getExpandedQueries() == null
                || dto.getExpandedQueries().isEmpty();

        if (isCanonicalEmpty || isExpandedEmpty) {
            log.warn("查询增强结果不完整 - canonicalQuery空: {}, expandedQueries空: {}",
                    isCanonicalEmpty, isExpandedEmpty);
            return StateGraph.END;
        }

        log.info("查询增强有效 → Schema 召回");
        return StateKey.SCHEMA_RECALL_NODE;
    }
}