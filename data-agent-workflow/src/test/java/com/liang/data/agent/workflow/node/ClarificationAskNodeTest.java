package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.workflow.dto.node.ClarificationRequestDTO;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.CLARIFICATION_NEXT_NODE;
import static com.liang.data.agent.common.constant.ControlFlowKey.MEMORY_CANDIDATE_NEXT_NODE;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_REQUEST;
import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static com.liang.data.agent.common.constant.StateKey.MEMORY_CANDIDATE_NODE;
import static com.liang.data.agent.common.constant.StateKey.SCHEMA_RECALL_NODE;
import static com.liang.data.agent.common.constant.StateKey.THREAD_ID;
import static org.assertj.core.api.Assertions.assertThat;

class ClarificationAskNodeTest {

    @Test
    void shouldRouteSchemaRecallClarificationThroughMemoryCandidateFirst() {
        QueryEnhanceOutputDTO query = new QueryEnhanceOutputDTO();
        query.setCanonicalQuery("core bottleneck");

        ClarificationAskNode node = new ClarificationAskNode();
        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                QUERY_ENHANCE_NODE_OUTPUT, query,
                AGENT_ID, "2",
                THREAD_ID, "thread-1"
        )));

        @SuppressWarnings("unchecked")
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator =
                (Flux<GraphResponse<StreamingOutput<ChatResponse>>>) result.get(CLARIFICATION_REQUEST);
        GraphResponse<StreamingOutput<ChatResponse>> done = generator.blockLast();

        @SuppressWarnings("unchecked")
        Map<String, Object> stateUpdate = (Map<String, Object>) done.resultValue().orElseThrow();
        assertThat(stateUpdate.get(CLARIFICATION_NEXT_NODE)).isEqualTo(MEMORY_CANDIDATE_NODE);
        assertThat(stateUpdate.get(MEMORY_CANDIDATE_NEXT_NODE)).isEqualTo(SCHEMA_RECALL_NODE);
    }

    @Test
    void shouldUseGenericQuestionWhenSchemaRecallMissing() {
        QueryEnhanceOutputDTO query = new QueryEnhanceOutputDTO();
        query.setCanonicalQuery("分析整体准点率");

        ClarificationAskNode node = new ClarificationAskNode();
        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                QUERY_ENHANCE_NODE_OUTPUT, query,
                AGENT_ID, "2",
                THREAD_ID, "thread-1"
        )));

        @SuppressWarnings("unchecked")
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator =
                (Flux<GraphResponse<StreamingOutput<ChatResponse>>>) result.get(CLARIFICATION_REQUEST);
        GraphResponse<StreamingOutput<ChatResponse>> done = generator.blockLast();

        @SuppressWarnings("unchecked")
        Map<String, Object> stateUpdate = (Map<String, Object>) done.resultValue().orElseThrow();
        ClarificationRequestDTO request = (ClarificationRequestDTO) stateUpdate.get(CLARIFICATION_REQUEST);
        assertThat(request.getQuestion())
                .contains("分析整体准点率")
                .contains("业务对象")
                .contains("指标口径")
                .doesNotContain("核心瓶颈")
                .doesNotContain("调用次数");
    }
}
