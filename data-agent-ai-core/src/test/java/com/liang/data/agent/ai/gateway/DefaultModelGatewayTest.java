package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.gateway.response.GatewayChunk;
import com.liang.data.agent.gateway.request.GatewayConstraints;
import com.liang.data.agent.gateway.response.GatewayResult;
import com.liang.data.agent.gateway.request.ModelCallMode;
import com.liang.data.agent.gateway.request.ModelGatewayRequest;
import com.liang.data.agent.gateway.prompt.ModelMessage;
import com.liang.data.agent.gateway.prompt.ModelMessageRole;
import com.liang.data.agent.gateway.prompt.ModelPrompt;
import com.liang.data.agent.gateway.response.ModelRoute;
import com.liang.data.agent.gateway.response.ModelUsage;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.gateway.context.GatewayReactorContext;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import com.liang.data.agent.gateway.error.ModelGatewayException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * DefaultModelGateway 测试，验证默认单模型网关的生命周期编排、异常映射与指标记录。
 */
class DefaultModelGatewayTest {

    private static final String SCENE_CODE = "test-scene";

    private static final ModelRoute ROUTE = new ModelRoute("openai", "gpt-4o-mini", 1, false);

    private static final ModelUsage USAGE = new ModelUsage(3, 5, 8);

    @Test
    void callShouldRejectStreamMode() {
        DefaultModelGateway gateway = gateway(mock(OpenAiCompatibleGatewayProvider.class),
                mock(ModelGatewayInvocationRecorder.class), null);

        assertThatThrownBy(() -> gateway.call(request(ModelCallMode.STREAM)).block())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void streamShouldRejectBlockMode() {
        DefaultModelGateway gateway = gateway(mock(OpenAiCompatibleGatewayProvider.class),
                mock(ModelGatewayInvocationRecorder.class), null);

        assertThatThrownBy(() -> gateway.stream(request(ModelCallMode.BLOCK)).collectList().block())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void callShouldRecordLifecycleInOrderWhenProviderSucceeded() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        doAnswer(invocation -> successResult(invocation.getArgument(0))).when(provider)
                .call(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = gateway(provider, recorder, null);

        GatewayResult result = gateway.call(request(ModelCallMode.BLOCK)).block();

        assertThat(result).isNotNull();
        assertThat(result.invocationId()).isNotBlank();
        InOrder inOrder = inOrder(recorder, provider);
        inOrder.verify(recorder).startInvocation(eq(result.invocationId()), any(GatewayExecutionContext.class),
                eq(SCENE_CODE), eq(ModelCallMode.BLOCK));
        inOrder.verify(recorder).startAttempt(eq(result.invocationId()), anyString(), eq(1),
                eq("unknown"), eq("unknown"));
        inOrder.verify(provider).call(eq(result.invocationId()), any(ModelGatewayRequest.class));
        inOrder.verify(recorder).finishAttempt(anyString(), eq(ModelGatewayCallStatus.SUCCEEDED),
                isNull(), isNull(), isNull());
        inOrder.verify(recorder).finishInvocation(eq(result.invocationId()), eq(ModelGatewayCallStatus.SUCCEEDED),
                eq(ROUTE), eq(USAGE), isNull(), isNull());
    }

    @Test
    void callShouldFinishFailedWhenProviderThrowsModelGatewayException() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        ModelGatewayException expectedException = new ModelGatewayException(ModelGatewayErrorCode.RESPONSE_INVALID);
        doThrow(expectedException).when(provider).call(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = gateway(provider, recorder, null);

        assertThatThrownBy(() -> gateway.call(request(ModelCallMode.BLOCK)).block())
                .isSameAs(expectedException)
                .isInstanceOfSatisfying(ModelGatewayException.class, exception ->
                        assertThat(exception.getGatewayErrorCode()).isEqualTo(ModelGatewayErrorCode.RESPONSE_INVALID));
        verify(recorder).finishAttempt(anyString(), eq(ModelGatewayCallStatus.FAILED),
                isNull(), eq(ModelGatewayErrorCode.RESPONSE_INVALID), anyString());
        verify(recorder).finishInvocation(anyString(), eq(ModelGatewayCallStatus.FAILED),
                isNull(), isNull(), eq(ModelGatewayErrorCode.RESPONSE_INVALID), anyString());
    }

    @Test
    void recorderFailureShouldNotAffectProviderResult() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        doAnswer(invocation -> successResult(invocation.getArgument(0))).when(provider)
                .call(anyString(), any(ModelGatewayRequest.class));
        doThrow(new IllegalStateException("记录开始失败")).when(recorder)
                .startInvocation(anyString(), any(GatewayExecutionContext.class), anyString(), any(ModelCallMode.class));
        doThrow(new IllegalStateException("记录尝试失败")).when(recorder)
                .startAttempt(anyString(), anyString(), anyInt(), anyString(), anyString());
        doThrow(new IllegalStateException("记录完成失败")).when(recorder)
                .finishAttempt(anyString(), any(ModelGatewayCallStatus.class), any(), any(), any());
        doThrow(new IllegalStateException("记录调用完成失败")).when(recorder)
                .finishInvocation(anyString(), any(ModelGatewayCallStatus.class), any(), any(), any(), any());
        DefaultModelGateway gateway = gateway(provider, recorder, null);

        GatewayResult result = gateway.call(request(ModelCallMode.BLOCK)).block();

        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("模型回答");
        verify(provider).call(eq(result.invocationId()), any(ModelGatewayRequest.class));
    }

    @Test
    void callShouldUseFallbackContextWhenReactorContextMissing() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        doAnswer(invocation -> successResult(invocation.getArgument(0))).when(provider)
                .call(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = gateway(provider, recorder, null);
        ArgumentCaptor<GatewayExecutionContext> contextCaptor = ArgumentCaptor.forClass(GatewayExecutionContext.class);

        gateway.call(request(ModelCallMode.BLOCK)).block();

        verify(recorder).startInvocation(anyString(), contextCaptor.capture(), eq(SCENE_CODE), eq(ModelCallMode.BLOCK));
        GatewayExecutionContext context = contextCaptor.getValue();
        assertThat(context.runId()).isNotBlank();
        assertThat(context.traceId()).isNotBlank();
        assertThat(context.traceId()).doesNotContain("-");
        assertThat(context.sessionId()).isEqualTo("model-gateway-standalone");
    }

    @Test
    void callShouldUseReactorContextWhenPresent() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        GatewayExecutionContext expectedContext = new GatewayExecutionContext("run-001", "trace-001",
                "session-001", 1L, 2, "tenant-001");
        doAnswer(invocation -> successResult(invocation.getArgument(0))).when(provider)
                .call(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = gateway(provider, recorder, null);
        ArgumentCaptor<GatewayExecutionContext> contextCaptor = ArgumentCaptor.forClass(GatewayExecutionContext.class);

        gateway.call(request(ModelCallMode.BLOCK))
                .contextWrite(GatewayReactorContext.with(expectedContext))
                .block();

        verify(recorder).startInvocation(anyString(), contextCaptor.capture(), eq(SCENE_CODE), eq(ModelCallMode.BLOCK));
        assertThat(contextCaptor.getValue()).isEqualTo(expectedContext);
    }

    @Test
    void callTimeoutShouldMapProviderTimeout() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        doAnswer(invocation -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return successResult(invocation.getArgument(0));
        }).when(provider).call(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = new DefaultModelGateway(provider, recorder, Duration.ofMillis(30), null);

        assertThatThrownBy(() -> gateway.call(request(ModelCallMode.BLOCK)).block())
                .isInstanceOfSatisfying(ModelGatewayException.class, exception ->
                        assertThat(exception.getGatewayErrorCode()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT));
        verify(recorder).finishAttempt(anyString(), eq(ModelGatewayCallStatus.FAILED),
                isNull(), eq(ModelGatewayErrorCode.PROVIDER_TIMEOUT), anyString());
        verify(recorder).finishInvocation(anyString(), eq(ModelGatewayCallStatus.FAILED),
                isNull(), isNull(), eq(ModelGatewayErrorCode.PROVIDER_TIMEOUT), anyString());
    }

    @Test
    void streamTimeoutShouldMapProviderTimeoutAndRecordFailedTerminalState() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        doReturn(Flux.never()).when(provider).stream(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = new DefaultModelGateway(provider, recorder, Duration.ofMillis(30), meterRegistry);

        assertThatThrownBy(() -> gateway.stream(request(ModelCallMode.STREAM)).collectList()
                .block(Duration.ofMillis(500)))
                .isInstanceOfSatisfying(ModelGatewayException.class, exception ->
                        assertThat(exception.getGatewayErrorCode()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT));
        verify(recorder).finishAttempt(anyString(), eq(ModelGatewayCallStatus.FAILED),
                isNull(), eq(ModelGatewayErrorCode.PROVIDER_TIMEOUT), anyString());
        verify(recorder).finishInvocation(anyString(), eq(ModelGatewayCallStatus.FAILED),
                isNull(), isNull(), eq(ModelGatewayErrorCode.PROVIDER_TIMEOUT), anyString());
        assertThat(meterRegistry.find("model_gateway_errors_total").counter())
                .isNotNull()
                .extracting(counter -> counter.count())
                .isEqualTo(1.0D);
    }

    @Test
    void callCancelShouldFinishCancelledOnlyOnce() throws InterruptedException {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        CountDownLatch providerStartedLatch = new CountDownLatch(1);
        CountDownLatch releaseProviderLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            providerStartedLatch.countDown();
            try {
                releaseProviderLatch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return successResult(invocation.getArgument(0));
        }).when(provider).call(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = gateway(provider, recorder, null);

        Disposable disposable = gateway.call(request(ModelCallMode.BLOCK)).subscribe();
        assertThat(providerStartedLatch.await(1, TimeUnit.SECONDS)).isTrue();
        disposable.dispose();

        try {
            verify(recorder, timeout(500)).finishAttempt(anyString(), eq(ModelGatewayCallStatus.CANCELLED),
                    isNull(), eq(ModelGatewayErrorCode.CALL_CANCELLED), anyString());
            verify(recorder, timeout(500)).finishInvocation(anyString(), eq(ModelGatewayCallStatus.CANCELLED),
                    isNull(), isNull(), eq(ModelGatewayErrorCode.CALL_CANCELLED), anyString());
            verify(recorder, never()).finishInvocation(anyString(), eq(ModelGatewayCallStatus.SUCCEEDED),
                    any(), any(), any(), any());
            verify(recorder, never()).finishInvocation(anyString(), eq(ModelGatewayCallStatus.FAILED),
                    any(), any(), any(), any());
            verify(recorder, times(1)).finishAttempt(anyString(), any(ModelGatewayCallStatus.class),
                    any(), any(), any());
            verify(recorder, times(1)).finishInvocation(anyString(), any(ModelGatewayCallStatus.class),
                    any(), any(), any(), any());
        } finally {
            releaseProviderLatch.countDown();
        }
    }

    @Test
    void metricsFailureShouldNotAffectProviderResultOrRecordFailure() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        doAnswer(invocation -> successResult(invocation.getArgument(0))).when(provider)
                .call(anyString(), any(ModelGatewayRequest.class));
        doThrow(new IllegalStateException("指标注册失败")).when(meterRegistry)
                .counter(eq("model_gateway_invocations_total"), any(String[].class));
        DefaultModelGateway gateway = new DefaultModelGateway(provider, recorder, Duration.ofSeconds(1), meterRegistry);

        GatewayResult result = gateway.call(request(ModelCallMode.BLOCK)).block();

        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("模型回答");
        verify(recorder).finishAttempt(anyString(), eq(ModelGatewayCallStatus.SUCCEEDED),
                isNull(), isNull(), isNull());
        verify(recorder).finishInvocation(anyString(), eq(ModelGatewayCallStatus.SUCCEEDED),
                eq(ROUTE), eq(USAGE), isNull(), isNull());
        verify(recorder, never()).finishAttempt(anyString(), eq(ModelGatewayCallStatus.FAILED),
                any(), any(), any());
        verify(recorder, never()).finishInvocation(anyString(), eq(ModelGatewayCallStatus.FAILED),
                any(), any(), any(), any());
    }

    @Test
    void streamShouldFinishSucceededWhenFinishedChunkReceived() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        doAnswer(invocation -> Flux.just(
                new GatewayChunk(invocation.getArgument(0), "增量", false, null, null, null),
                new GatewayChunk(invocation.getArgument(0), "", true, USAGE, ROUTE, "stop")
        )).when(provider).stream(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = gateway(provider, recorder, null);

        List<GatewayChunk> chunks = gateway.stream(request(ModelCallMode.STREAM)).collectList().block();

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).finished()).isFalse();
        assertThat(chunks.get(1).finished()).isTrue();
        verify(recorder).finishAttempt(anyString(), eq(ModelGatewayCallStatus.SUCCEEDED),
                isNull(), isNull(), isNull());
        verify(recorder).finishInvocation(anyString(), eq(ModelGatewayCallStatus.SUCCEEDED),
                eq(ROUTE), eq(USAGE), isNull(), isNull());
    }

    @Test
    void streamShouldFinishFailedWhenProviderStreamErrors() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        ModelGatewayException expectedException = new ModelGatewayException(ModelGatewayErrorCode.PROVIDER_UNAVAILABLE);
        doReturn(Flux.error(expectedException)).when(provider).stream(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = gateway(provider, recorder, null);

        assertThatThrownBy(() -> gateway.stream(request(ModelCallMode.STREAM)).collectList().block())
                .isSameAs(expectedException);
        verify(recorder).finishAttempt(anyString(), eq(ModelGatewayCallStatus.FAILED),
                isNull(), eq(ModelGatewayErrorCode.PROVIDER_UNAVAILABLE), anyString());
        verify(recorder).finishInvocation(anyString(), eq(ModelGatewayCallStatus.FAILED),
                isNull(), isNull(), eq(ModelGatewayErrorCode.PROVIDER_UNAVAILABLE), anyString());
    }

    @Test
    void streamCancelShouldFinishCancelled() throws InterruptedException {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        doAnswer(invocation -> Flux.<GatewayChunk>create(sink -> {
            sink.next(new GatewayChunk(invocation.getArgument(0), "增量", false, null, null, null));
            sink.onCancel(cancelLatch::countDown);
        })).when(provider).stream(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = gateway(provider, recorder, null);

        Disposable disposable = gateway.stream(request(ModelCallMode.STREAM)).subscribe();
        disposable.dispose();

        assertThat(cancelLatch.await(1, TimeUnit.SECONDS)).isTrue();
        verify(recorder).finishAttempt(anyString(), eq(ModelGatewayCallStatus.CANCELLED),
                isNull(), eq(ModelGatewayErrorCode.CALL_CANCELLED), anyString());
        verify(recorder).finishInvocation(anyString(), eq(ModelGatewayCallStatus.CANCELLED),
                isNull(), isNull(), eq(ModelGatewayErrorCode.CALL_CANCELLED), anyString());
    }

    @Test
    void metricsTagsShouldNotContainSensitiveIdentifiers() {
        OpenAiCompatibleGatewayProvider provider = mock(OpenAiCompatibleGatewayProvider.class);
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        doAnswer(invocation -> successResult(invocation.getArgument(0))).when(provider)
                .call(anyString(), any(ModelGatewayRequest.class));
        DefaultModelGateway gateway = gateway(provider, recorder, meterRegistry);

        gateway.call(request(ModelCallMode.BLOCK)).block();

        Set<String> tagKeys = meterRegistry.getMeters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(tag -> tag.getKey())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(tagKeys).contains("scene_code", "provider", "model", "status", "error_code");
        assertThat(tagKeys).doesNotContain("runId", "traceId", "invocationId",
                "run_id", "trace_id", "invocation_id");
    }

    private static DefaultModelGateway gateway(OpenAiCompatibleGatewayProvider provider,
                                               ModelGatewayInvocationRecorder recorder,
                                               SimpleMeterRegistry meterRegistry) {
        return new DefaultModelGateway(provider, recorder, Duration.ofSeconds(1), meterRegistry);
    }

    private static GatewayResult successResult(String invocationId) {
        return new GatewayResult(invocationId, "模型回答", USAGE, ROUTE, "stop");
    }

    private static ModelGatewayRequest request(ModelCallMode mode) {
        List<ModelMessage> messages = List.of(
                new ModelMessage(ModelMessageRole.SYSTEM, "你是助手"),
                new ModelMessage(ModelMessageRole.USER, "你好")
        );
        return new ModelGatewayRequest(SCENE_CODE, ModelPrompt.direct(messages), mode,
                GatewayConstraints.defaults(), Map.of());
    }
}
