package com.liang.data.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.liang.data.agent.workflow.dto.node.IntentRecognitionOutputDTO;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.liang.data.agent.common.constant.NodeOutputKey.INTENT_RECOGNITION_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.EVIDENCE_RECALL_NODE;

/**
 * 意图识别路由
 *
 * <p>闲聊/无关 → END, 数据分析请求 → EvidenceRecallNode</p>
 *
 * <p>无外部依赖，不需要 @Component，在 WorkflowConfiguration 中 new 即可</p>
 */
@Slf4j
public class IntentRecognitionDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) throws Exception {
        // 获取意图识别结果
        var res = StateUtil.getObjectValue(state, INTENT_RECOGNITION_NODE_OUTPUT, IntentRecognitionOutputDTO.class);

        if (res.getClassification() == null || res.getClassification().trim().isEmpty()) {
            log.warn("意图识别结果为空，默认结束");
            return StateGraph.END;
        }

        String classification = res.getClassification();
        if ("《闲聊或无关指令》".equals(classification)) {
            log.info("意图分类: 闲聊/无关 → 结束");
            return StateGraph.END;
        } else {
            log.info("意图分类: 数据分析请求 → 证据召回");
            return EVIDENCE_RECALL_NODE;
        }
    }
}
