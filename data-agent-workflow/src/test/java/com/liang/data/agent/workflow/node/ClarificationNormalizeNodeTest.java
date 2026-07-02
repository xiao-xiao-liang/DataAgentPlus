package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.gateway.api.ModelGatewayScenes;
import com.liang.data.agent.workflow.dto.node.ClarificationNormalizedDTO;
import com.liang.data.agent.workflow.dto.node.ClarificationRequestDTO;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.CLARIFICATION_NEXT_NODE;
import static com.liang.data.agent.common.constant.ControlFlowKey.WAIT_FOR_CLARIFICATION_CONFIRM;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_FEEDBACK_DATA;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_NORMALIZED_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_REQUEST;
import static com.liang.data.agent.common.constant.NodeOutputKey.EVIDENCE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static com.liang.data.agent.common.constant.StateKey.INPUT_KEY;
import static com.liang.data.agent.common.constant.StateKey.SCHEMA_RECALL_NODE;
import static com.liang.data.agent.common.constant.StateKey.THREAD_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClarificationNormalizeNodeTest {

    @Test
    void shouldNotOverwriteRouteAfterConfirmation() {
        LlmService llmService = mock(LlmService.class);
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);
        when(llmService.callUser(eq(ModelGatewayScenes.CLARIFICATION_NORMALIZE), anyString()))
                .thenReturn(Flux.just(ChatResponseUtil.createResponse("{}")));

        ClarificationNormalizedDTO normalized = new ClarificationNormalizedDTO();
        normalized.setTerm("核心瓶颈");
        normalized.setNormalizedDefinition("按耗时、错误率、调用次数定位瓶颈");
        normalized.setConfirmationText("请确认是否继续");
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(ClarificationNormalizedDTO.class)))
                .thenReturn(normalized);

        ClarificationRequestDTO request = new ClarificationRequestDTO();
        request.setQuestion("请补充核心瓶颈的衡量口径");

        Map<String, Object> data = new HashMap<>();
        data.put(CLARIFICATION_REQUEST, request);
        data.put(CLARIFICATION_FEEDBACK_DATA, Map.of("answer", "耗时/错误率/调用次数"));
        data.put(CLARIFICATION_NEXT_NODE, SCHEMA_RECALL_NODE);
        data.put(INPUT_KEY, "帮我分析下系统链路的核心瓶颈在哪里");
        data.put(EVIDENCE_OUTPUT, "");
        data.put(AGENT_ID, "2");
        data.put(THREAD_ID, "thread-1");

        ClarificationNormalizeNode node = new ClarificationNormalizeNode(llmService, jsonParseUtil);
        Map<String, Object> result = node.apply(new OverAllState(data));

        @SuppressWarnings("unchecked")
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator =
                (Flux<GraphResponse<StreamingOutput<ChatResponse>>>) result.get(CLARIFICATION_NORMALIZED_OUTPUT);
        GraphResponse<StreamingOutput<ChatResponse>> done = generator.blockLast();

        @SuppressWarnings("unchecked")
        Map<String, Object> stateUpdate = (Map<String, Object>) done.resultValue().orElseThrow();
        assertTrue(stateUpdate.containsKey(CLARIFICATION_NORMALIZED_OUTPUT));
        assertTrue(stateUpdate.containsKey(WAIT_FOR_CLARIFICATION_CONFIRM));
        assertFalse(stateUpdate.containsKey(CLARIFICATION_NEXT_NODE));
    }
}
