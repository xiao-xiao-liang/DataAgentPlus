package com.liang.data.agent.workflow.node;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.workflow.dto.node.IntentRecognitionOutputDTO;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static com.liang.data.agent.common.constant.NodeOutputKey.INTENT_RECOGNITION_NODE_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentRecognitionNodeTest {

    @Test
    void shouldParseIntentRecognitionResult() {
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);
        IntentRecognitionOutputDTO dto = new IntentRecognitionOutputDTO();
        dto.setClassification("《可能的数据分析请求》");
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(IntentRecognitionOutputDTO.class))).thenReturn(dto);

        IntentRecognitionNode node = new IntentRecognitionNode(mock(LlmService.class), jsonParseUtil);

        Map<String, ?> result = ReflectionTestUtils.invokeMethod(
                node,
                "handleIntentRecognition",
                "```json\n{\"classification\":\"《可能的数据分析请求》\"}\n```"
        );

        assertEquals(dto, result.get(INTENT_RECOGNITION_NODE_OUTPUT));
    }

    @Test
    void shouldReturnEmptyMapWhenIntentRecognitionParsingFails() {
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(IntentRecognitionOutputDTO.class)))
                .thenThrow(new IllegalArgumentException("bad json"));

        IntentRecognitionNode node = new IntentRecognitionNode(mock(LlmService.class), jsonParseUtil);

        Map<String, ?> result = ReflectionTestUtils.invokeMethod(
                node,
                "handleIntentRecognition",
                "not-json"
        );

        assertTrue(result.isEmpty());
    }
}