package com.liang.data.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.liang.data.agent.common.constant.StateKey;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.util.List;

import static com.liang.data.agent.common.constant.NodeOutputKey.TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT;

@Slf4j
public class SchemaRecallDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        List<Document> docs = StateUtil.getDocumentList(state, TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT);
        if (!docs.isEmpty()) {
            return StateKey.TABLE_RELATION_NODE;
        }
        log.info("未找到表文档，转入问题澄清");
        return StateKey.CLARIFICATION_ASK_NODE;
    }
}
