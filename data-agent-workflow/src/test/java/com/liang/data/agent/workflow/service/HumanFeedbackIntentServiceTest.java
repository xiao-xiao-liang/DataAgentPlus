package com.liang.data.agent.workflow.service;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.workflow.dto.humanfeedback.HumanFeedbackIntent;
import com.liang.data.agent.workflow.dto.humanfeedback.HumanFeedbackIntentResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

class HumanFeedbackIntentServiceTest {

    @Test
    void shouldApproveExplicitPositiveFeedbackByRule() {
        HumanFeedbackIntentService service = new HumanFeedbackIntentService(new FixedLlmService(""));

        HumanFeedbackIntentResult result = service.classify("对的，就是这样，开始任务吧");

        assertThat(result.getIntent()).isEqualTo(HumanFeedbackIntent.APPROVE);
    }

    @Test
    void shouldReviseWhenPositiveFeedbackContainsModificationSignal() {
        HumanFeedbackIntentService service = new HumanFeedbackIntentService(new FixedLlmService(""));

        HumanFeedbackIntentResult result = service.classify("可以，但是把时间范围改成本月");

        assertThat(result.getIntent()).isEqualTo(HumanFeedbackIntent.REVISE);
    }

    @Test
    void shouldApproveHighConfidenceModelPositiveFeedback() {
        HumanFeedbackIntentService service = new HumanFeedbackIntentService(new FixedLlmService("""
                {"intent":"APPROVE","confidence":0.96,"reason":"用户确认当前计划","hasModificationRequest":false}
                """));

        HumanFeedbackIntentResult result = service.classify("对，就按前面那个方案处理");

        assertThat(result.getIntent()).isEqualTo(HumanFeedbackIntent.APPROVE);
    }

    @Test
    void shouldKeepUncertainWhenModelConfidenceIsLow() {
        HumanFeedbackIntentService service = new HumanFeedbackIntentService(new FixedLlmService("""
                {"intent":"APPROVE","confidence":0.65,"reason":"表达较模糊","hasModificationRequest":false}
                """));

        HumanFeedbackIntentResult result = service.classify("差不多吧");

        assertThat(result.getIntent()).isEqualTo(HumanFeedbackIntent.UNCERTAIN);
    }

    @Test
    void shouldReviseWhenModelDetectsModificationRequest() {
        HumanFeedbackIntentService service = new HumanFeedbackIntentService(new FixedLlmService("""
                {"intent":"APPROVE","confidence":0.95,"reason":"含确认词但也有修改要求","hasModificationRequest":true}
                """));

        HumanFeedbackIntentResult result = service.classify("可以，第二步再优化一下");

        assertThat(result.getIntent()).isEqualTo(HumanFeedbackIntent.REVISE);
    }

    private record FixedLlmService(String response) implements LlmService {

        @Override
        public Flux<ChatResponse> call(String system, String user) {
            return Flux.just(ChatResponseUtil.createResponse(response));
        }

        @Override
        public Flux<ChatResponse> callSystem(String system) {
            return Flux.just(ChatResponseUtil.createResponse(response));
        }

        @Override
        public Flux<ChatResponse> callUser(String user) {
            return Flux.just(ChatResponseUtil.createResponse(response));
        }
    }
}
