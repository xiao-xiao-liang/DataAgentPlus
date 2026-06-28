package com.liang.data.agent.service.ratelimit;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.gateway.api.ModelGatewayScenes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void callWithSceneCodeShouldInvokeDelegateSceneCodeMethodWhenPermitAcquired() {
        LlmService delegate = mock(LlmService.class, CALLS_REAL_METHODS);
        when(delegate.call(eq(ModelGatewayScenes.SQL_GENERATION), eq("system"), eq("user")))
                .thenReturn(Flux.just(ChatResponseUtil.createPureResponse("ok")));
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.LLM_CALL), anyString(), any(Duration.class)))
                .thenReturn(ResourcePermit.acquired(ResourceType.LLM_CALL, "llm-call", () -> {
                }));
        ResourceGatedLlmService service = new ResourceGatedLlmService(delegate, resourceGate);

        // 1. 调用携带场景编码的显式重载
        List<ChatResponse> responses = service.call(ModelGatewayScenes.SQL_GENERATION, "system", "user")
                .collectList()
                .block();

        // 2. 校验委托到显式重载，且不会回退到旧重载
        assertThat(responses).hasSize(1);
        assertThat(ChatResponseUtil.getText(responses.getFirst())).isEqualTo("ok");
        verify(delegate).call(ModelGatewayScenes.SQL_GENERATION, "system", "user");
        verify(delegate, never()).call("system", "user");
    }

    @Test
    void callSystemWithSceneCodeShouldInvokeDelegateSceneCodeMethodWhenPermitAcquired() {
        LlmService delegate = mock(LlmService.class, CALLS_REAL_METHODS);
        when(delegate.callSystem(eq(ModelGatewayScenes.SQL_GENERATION), eq("system")))
                .thenReturn(Flux.just(ChatResponseUtil.createPureResponse("ok")));
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.LLM_CALL), anyString(), any(Duration.class)))
                .thenReturn(ResourcePermit.acquired(ResourceType.LLM_CALL, "llm-call-system", () -> {
                }));
        ResourceGatedLlmService service = new ResourceGatedLlmService(delegate, resourceGate);

        // 1. 调用携带场景编码的系统消息显式重载
        List<ChatResponse> responses = service.callSystem(ModelGatewayScenes.SQL_GENERATION, "system")
                .collectList()
                .block();

        // 2. 校验委托到显式重载，且不会回退到旧重载
        assertThat(responses).hasSize(1);
        assertThat(ChatResponseUtil.getText(responses.getFirst())).isEqualTo("ok");
        verify(delegate).callSystem(ModelGatewayScenes.SQL_GENERATION, "system");
        verify(delegate, never()).callSystem("system");
    }

    @Test
    void callWithInvalidSceneCodeShouldUseUnknownSceneOwnerId() {
        LlmService delegate = mock(LlmService.class, CALLS_REAL_METHODS);
        when(delegate.call(anyString(), eq("system"), eq("user")))
                .thenReturn(Flux.just(ChatResponseUtil.createPureResponse("ok")));
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.LLM_CALL), anyString(), any(Duration.class)))
                .thenReturn(ResourcePermit.acquired(ResourceType.LLM_CALL, "llm-call:unknown_scene", () -> {
                }));
        ResourceGatedLlmService service = new ResourceGatedLlmService(delegate, resourceGate);

        // 1. 使用包含空格和中文提示词片段的非法场景编码发起调用
        service.call("sql 生成 prompt 片段", "system", "user").collectList().block();

        // 2. 校验资源占用方标识只包含固定降级值，不包含原始非法输入
        ArgumentCaptor<String> ownerIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(resourceGate).tryAcquire(eq(ResourceType.LLM_CALL), ownerIdCaptor.capture(), eq(Duration.ZERO));
        assertThat(ownerIdCaptor.getValue()).isEqualTo("llm-call:unknown_scene");
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

    @Test
    void callShouldReleasePermitWhenDelegateThrowsBeforeReturningFlux() {
        LlmService delegate = mock(LlmService.class, CALLS_REAL_METHODS);
        IllegalStateException exception = new IllegalStateException("delegate 同步异常");
        when(delegate.call(eq("system"), eq("user"))).thenThrow(exception);
        AtomicBoolean released = new AtomicBoolean(false);
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.LLM_CALL), anyString(), any(Duration.class)))
                .thenReturn(ResourcePermit.acquired(ResourceType.LLM_CALL, "llm-call",
                        () -> released.set(true)));
        ResourceGatedLlmService service = new ResourceGatedLlmService(delegate, resourceGate);

        // 1. delegate 在返回 Flux 前同步抛异常时，服务应返回错误流
        Flux<ChatResponse> responses = service.call("system", "user");

        // 2. 校验许可已释放，且订阅错误流时仍能收到原始异常
        assertThat(released).isTrue();
        assertThatThrownBy(() -> responses.collectList().block()).isSameAs(exception);
    }
}
