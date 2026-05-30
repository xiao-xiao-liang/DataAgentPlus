package com.liang.data.agent.workflow.util;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.workflow.dto.node.FeasibilityAssessmentOutputDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonParseUtilTest {

    @Test
    void shouldParseJsonWrappedInMarkdownFenceWithoutLlmRepair() {
        JsonParseUtil jsonParseUtil = new JsonParseUtil(new FailingLlmService());

        FeasibilityAssessmentOutputDTO output = jsonParseUtil.tryConvertToObject("""
                ```json
                {
                  "requestType": "DATA_ANALYSIS",
                  "analysisGoal": "find bottleneck",
                  "memoryWorthSaving": false,
                  "affectsSchemaRecall": false
                }
                ```
                """, FeasibilityAssessmentOutputDTO.class);

        assertEquals("DATA_ANALYSIS", output.getRequestType());
        assertEquals("find bottleneck", output.getAnalysisGoal());
        assertEquals(false, output.getMemoryWorthSaving());
        assertEquals(false, output.getAffectsSchemaRecall());
    }

    @Test
    void shouldUsePlainTextFromLlmRepairResponse() {
        JsonParseUtil jsonParseUtil = new JsonParseUtil(new FixedLlmService("""
                {"requestType":"DATA_ANALYSIS","analysisGoal":"repaired"}
                """));

        FeasibilityAssessmentOutputDTO output = jsonParseUtil.tryConvertToObject(
                "{\"requestType\":\"DATA_ANALYSIS\",\"analysisGoal\":}",
                FeasibilityAssessmentOutputDTO.class
        );

        assertEquals("DATA_ANALYSIS", output.getRequestType());
        assertEquals("repaired", output.getAnalysisGoal());
    }

    private static class FailingLlmService implements LlmService {
        @Override
        public Flux<ChatResponse> call(String system, String user) {
            throw new AssertionError("LLM repair should not be called");
        }

        @Override
        public Flux<ChatResponse> callSystem(String system) {
            throw new AssertionError("LLM repair should not be called");
        }

        @Override
        public Flux<ChatResponse> callUser(String user) {
            throw new AssertionError("LLM repair should not be called");
        }
    }

    private static class FixedLlmService implements LlmService {
        private final String fixedJson;

        private FixedLlmService(String fixedJson) {
            this.fixedJson = fixedJson;
        }

        @Override
        public Flux<ChatResponse> call(String system, String user) {
            return callUser(user);
        }

        @Override
        public Flux<ChatResponse> callSystem(String system) {
            return callUser(system);
        }

        @Override
        public Flux<ChatResponse> callUser(String user) {
            return Flux.just(ChatResponseUtil.createPureResponse(fixedJson));
        }
    }
}
