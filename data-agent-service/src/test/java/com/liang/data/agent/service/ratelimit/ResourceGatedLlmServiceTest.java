package com.liang.data.agent.service.ratelimit;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.gateway.api.ModelGatewayScenes;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资源门控 LLM 服务测试。
 */
class ResourceGatedLlmServiceTest {

    @Test
    void callUserShouldNotInvokeDelegateWhenPermitRejected() {
        LlmService delegate = mock(LlmService.class, CALLS_REAL_METHODS);
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.LLM_CALL), anyString(), any(Duration.class)))
                .thenReturn(ResourcePermit.rejected(ResourceType.LLM_CALL, "llm-call-user"));
        ResourceGatedLlmService service = new ResourceGatedLlmService(delegate, resourceGate);

        List<ChatResponse> responses = service.callUser("hello").collectList().block();

        assertThat(responses).hasSize(1);
        assertThat(ChatResponseUtil.getText(responses.getFirst())).contains("大模型资源繁忙");
        verify(delegate, never()).callUser(anyString());
    }

    @Test
    void callUserWithSceneCodeShouldInvokeDelegateSceneCodeMethodWhenPermitAcquired() {
        LlmService delegate = mock(LlmService.class, CALLS_REAL_METHODS);
        when(delegate.callUser(eq(ModelGatewayScenes.SQL_GENERATION), eq("hello")))
                .thenReturn(Flux.just(ChatResponseUtil.createPureResponse("ok")));
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.LLM_CALL), anyString(), any(Duration.class)))
                .thenReturn(ResourcePermit.acquired(ResourceType.LLM_CALL, "llm-call-user", () -> {
                }));
        ResourceGatedLlmService service = new ResourceGatedLlmService(delegate, resourceGate);

        List<ChatResponse> responses = service.callUser(ModelGatewayScenes.SQL_GENERATION, "hello")
                .collectList()
                .block();

        assertThat(responses).hasSize(1);
        assertThat(ChatResponseUtil.getText(responses.getFirst())).isEqualTo("ok");
        verify(delegate).callUser(ModelGatewayScenes.SQL_GENERATION, "hello");
        verify(delegate, never()).callUser("hello");
    }

    @Test
    void callUserWithSceneCodeShouldNotInvokeDelegateWhenPermitRejected() {
        LlmService delegate = mock(LlmService.class, CALLS_REAL_METHODS);
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.LLM_CALL), anyString(), any(Duration.class)))
                .thenReturn(ResourcePermit.rejected(ResourceType.LLM_CALL, "llm-call-user"));
        ResourceGatedLlmService service = new ResourceGatedLlmService(delegate, resourceGate);

        List<ChatResponse> responses = service.callUser(ModelGatewayScenes.SQL_GENERATION, "hello")
                .collectList()
                .block();

        assertThat(responses).hasSize(1);
        assertThat(ChatResponseUtil.getText(responses.getFirst())).contains("大模型资源繁忙");
        verify(delegate, never()).callUser(anyString(), anyString());
        verify(delegate, never()).callUser(anyString());
    }

    @Test
    void callShouldReleasePermitWhenDelegateStreamCompletes() {
        LlmService delegate = mock(LlmService.class, CALLS_REAL_METHODS);
        when(delegate.call(anyString(), anyString())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse("ok")));
        AtomicBoolean released = new AtomicBoolean(false);
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.LLM_CALL), anyString(), any(Duration.class)))
                .thenReturn(ResourcePermit.acquired(ResourceType.LLM_CALL, "llm-call",
                        () -> released.set(true)));
        ResourceGatedLlmService service = new ResourceGatedLlmService(delegate, resourceGate);

        List<ChatResponse> responses = service.call("system", "user").collectList().block();

        assertThat(responses).hasSize(1);
        assertThat(ChatResponseUtil.getText(responses.getFirst())).isEqualTo("ok");
        assertThat(released).isTrue();
    }
}
