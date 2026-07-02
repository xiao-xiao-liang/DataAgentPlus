package com.liang.data.agent.ai.llm;

import com.liang.data.agent.gateway.api.ModelGatewayScenes;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 服务显式场景编码默认方法测试。
 */
class LlmServiceSceneMethodTest {

    @Test
    void callUserWithSceneCodeShouldFallbackToLegacyCallUser() {
        RecordingLlmService service = new RecordingLlmService();

        service.callUser(ModelGatewayScenes.SQL_GENERATION, "生成SQL").collectList().block();

        assertThat(service.calls()).containsExactly("callUser:生成SQL");
    }

    @Test
    void callSystemWithSceneCodeShouldFallbackToLegacyCallSystem() {
        RecordingLlmService service = new RecordingLlmService();

        service.callSystem(ModelGatewayScenes.INTENT_RECOGNITION, "系统提示").collectList().block();

        assertThat(service.calls()).containsExactly("callSystem:系统提示");
    }

    @Test
    void callWithSceneCodeShouldFallbackToLegacyCall() {
        RecordingLlmService service = new RecordingLlmService();

        service.call(ModelGatewayScenes.PLANNER, "系统提示", "用户提示").collectList().block();

        assertThat(service.calls()).containsExactly("call:系统提示:用户提示");
    }

    @Test
    void legacyMethodsShouldStillBeAvailable() {
        RecordingLlmService service = new RecordingLlmService();

        service.call("系统提示", "用户提示").collectList().block();
        service.callSystem("系统提示").collectList().block();
        service.callUser("用户提示").collectList().block();

        assertThat(service.calls()).containsExactly(
                "call:系统提示:用户提示",
                "callSystem:系统提示",
                "callUser:用户提示");
    }

    /**
     * 记录旧入口调用顺序的 LLM 服务测试实现。
     */
    private static class RecordingLlmService implements LlmService {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Flux<ChatResponse> call(String system, String user) {
            calls.add("call:" + system + ":" + user);
            return Flux.empty();
        }

        @Override
        public Flux<ChatResponse> callSystem(String system) {
            calls.add("callSystem:" + system);
            return Flux.empty();
        }

        @Override
        public Flux<ChatResponse> callUser(String user) {
            calls.add("callUser:" + user);
            return Flux.empty();
        }

        /**
         * 返回已记录的调用序列。
         *
         * @return 调用序列
         */
        private List<String> calls() {
            return calls;
        }
    }
}
