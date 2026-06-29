package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.ai.model.AiModelRegistry;
import com.liang.data.agent.ai.model.DynamicModelFactory;
import com.liang.data.agent.ai.model.ModelConfigQueryService;
import com.liang.data.agent.common.enums.ModelType;
import com.liang.data.agent.dal.entity.ModelConfigEntity;
import com.liang.data.agent.gateway.api.GatewayChunk;
import com.liang.data.agent.gateway.api.GatewayConstraints;
import com.liang.data.agent.gateway.api.GatewayResult;
import com.liang.data.agent.gateway.api.ModelCallMode;
import com.liang.data.agent.gateway.api.ModelGatewayRequest;
import com.liang.data.agent.gateway.api.ModelMessage;
import com.liang.data.agent.gateway.api.ModelMessageRole;
import com.liang.data.agent.gateway.api.ModelPrompt;
import com.liang.data.agent.gateway.api.ModelUsage;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import com.liang.data.agent.gateway.error.ModelGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenAI 兼容模型供应商适配器测试。
 */
class OpenAiCompatibleGatewayProviderTest {

    @Test
    void callShouldReturnGatewayResultWithContentUsageRouteAndInvocationId() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("模型回答", 7, 11, 999),
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        GatewayResult result = provider.call("invocation-block", request(ModelCallMode.BLOCK));

        assertThat(result.invocationId()).isEqualTo("invocation-block");
        assertThat(result.content()).isEqualTo("模型回答");
        assertThat(result.usage()).isEqualTo(new ModelUsage(7, 11, 18));
        assertThat(result.route().provider()).isEqualTo("openai");
        assertThat(result.route().model()).isEqualTo("gpt-4o-mini");
        assertThat(result.route().attemptCount()).isEqualTo(1);
        assertThat(result.route().degraded()).isFalse();
        assertThat(result.finishReason()).isEqualTo("stop");
    }

    @Test
    void streamShouldReturnTextChunksAndFinalChunkWithUsageAndRoute() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.just(
                        chatResponse("增量一", 0, 0, 0),
                        chatResponse("增量二", 3, 5, 100)
                ),
                safeRouteSupplier("deepseek", "deepseek-chat")
        );

        List<GatewayChunk> chunks = provider.stream("invocation-stream", request(ModelCallMode.STREAM))
                .collectList()
                .block();

        assertThat(chunks).hasSize(3);
        assertTextChunk(chunks.get(0), "invocation-stream", "增量一");
        assertTextChunk(chunks.get(1), "invocation-stream", "增量二");
        GatewayChunk finishedChunk = chunks.get(2);
        assertThat(finishedChunk.invocationId()).isEqualTo("invocation-stream");
        assertThat(finishedChunk.content()).isEmpty();
        assertThat(finishedChunk.finished()).isTrue();
        assertThat(finishedChunk.usage()).isEqualTo(new ModelUsage(3, 5, 8));
        assertThat(finishedChunk.route().provider()).isEqualTo("deepseek");
        assertThat(finishedChunk.route().model()).isEqualTo("deepseek-chat");
        assertThat(finishedChunk.finishReason()).isEqualTo("stop");
    }

    @Test
    void callShouldMapTimeoutExceptionToProviderTimeout() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> {
                    throw new RuntimeException(new TimeoutException("请求超时"));
                },
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.call("invocation-timeout", request(ModelCallMode.BLOCK)),
                ModelGatewayErrorCode.PROVIDER_TIMEOUT);
    }

    @Test
    void streamShouldMapTimeoutExceptionToProviderTimeout() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.error(new RuntimeException(new TimeoutException("请求超时"))),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.stream("invocation-stream-timeout", request(ModelCallMode.STREAM))
                .collectList()
                .block(), ModelGatewayErrorCode.PROVIDER_TIMEOUT);
    }

    @Test
    void callShouldMap401MessageToAuthenticationFailed() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> {
                    throw new IllegalStateException("HTTP 401 unauthorized");
                },
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.call("invocation-auth-401", request(ModelCallMode.BLOCK)),
                ModelGatewayErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    void callShouldMap403MessageToAuthenticationFailed() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> {
                    throw new IllegalStateException("HTTP 403 forbidden");
                },
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.call("invocation-auth", request(ModelCallMode.BLOCK)),
                ModelGatewayErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    void callShouldMap429MessageToRateLimited() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> {
                    throw new IllegalStateException("HTTP 429 too many requests");
                },
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.call("invocation-rate", request(ModelCallMode.BLOCK)),
                ModelGatewayErrorCode.RATE_LIMITED);
    }

    @Test
    void streamShouldMap403MessageToAuthenticationFailed() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.error(new IllegalStateException("HTTP 403 forbidden")),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.stream("invocation-stream-auth", request(ModelCallMode.STREAM))
                .collectList()
                .block(), ModelGatewayErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    void streamShouldMap429MessageToRateLimited() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.error(new IllegalStateException("HTTP 429 too many requests")),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.stream("invocation-stream-rate", request(ModelCallMode.STREAM))
                .collectList()
                .block(), ModelGatewayErrorCode.RATE_LIMITED);
    }

    @Test
    void callShouldMapNullResponseToResponseInvalid() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> null,
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.call("invocation-null", request(ModelCallMode.BLOCK)),
                ModelGatewayErrorCode.RESPONSE_INVALID);
    }

    @Test
    void callShouldMapBlankResponseTextToResponseInvalid() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("   ", 0, 0, 0),
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.call("invocation-empty", request(ModelCallMode.BLOCK)),
                ModelGatewayErrorCode.RESPONSE_INVALID);
    }

    @Test
    void callShouldMapEmptyResponseTextToResponseInvalid() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("", 0, 0, 0),
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.call("invocation-empty-text", request(ModelCallMode.BLOCK)),
                ModelGatewayErrorCode.RESPONSE_INVALID);
    }

    @Test
    void streamShouldMapEmptyStreamToResponseInvalid() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.stream("invocation-empty-stream", request(ModelCallMode.STREAM))
                .collectList()
                .block(), ModelGatewayErrorCode.RESPONSE_INVALID);
    }

    @Test
    void streamShouldMapBlankTextToResponseInvalid() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.just(chatResponse("   ", 0, 0, 0)),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.stream("invocation-blank-stream", request(ModelCallMode.STREAM))
                .collectList()
                .block(), ModelGatewayErrorCode.RESPONSE_INVALID);
    }

    @Test
    void streamShouldMapNullResponseToResponseInvalid() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.from(subscriber -> subscriber.onSubscribe(new org.reactivestreams.Subscription() {

                    private boolean emitted;

                    @Override
                    public void request(long n) {
                        if (!emitted && n > 0) {
                            emitted = true;
                            subscriber.onNext(null);
                            subscriber.onComplete();
                        }
                    }

                    @Override
                    public void cancel() {
                        // 1. 测试发布器无需释放外部资源。
                    }
                })),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.stream("invocation-null-stream", request(ModelCallMode.STREAM))
                .collectList()
                .block(), ModelGatewayErrorCode.RESPONSE_INVALID);
    }

    @Test
    void callShouldMapProviderUnavailableSignalsToProviderUnavailable() {
        List<RuntimeException> exceptions = List.of(
                new IllegalStateException("HTTP 500 internal server error"),
                new IllegalStateException("connect refused"),
                new IllegalStateException("service unavailable")
        );

        for (RuntimeException exception : exceptions) {
            OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                    messages -> {
                        throw exception;
                    },
                    messages -> Flux.empty(),
                    safeRouteSupplier("openai", "gpt-4o-mini")
            );

            assertGatewayException(() -> provider.call("invocation-unavailable", request(ModelCallMode.BLOCK)),
                    ModelGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    @Test
    void callShouldPropagateExistingModelGatewayException() {
        ModelGatewayException expectedException = new ModelGatewayException(ModelGatewayErrorCode.RESPONSE_INVALID);
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> {
                    throw expectedException;
                },
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertThatThrownBy(() -> provider.call("invocation-existing-exception", request(ModelCallMode.BLOCK)))
                .isSameAs(expectedException);
    }

    @Test
    void callShouldDropProviderExceptionCauseAndSensitiveMessage() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> {
                    throw new IllegalStateException("HTTP 401 Authorization: Bearer sk-secret prompt=用户完整问题");
                },
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertThatThrownBy(() -> provider.call("invocation-sensitive-exception", request(ModelCallMode.BLOCK)))
                .isInstanceOfSatisfying(ModelGatewayException.class, exception -> {
                    assertThat(exception.getGatewayErrorCode()).isEqualTo(ModelGatewayErrorCode.AUTHENTICATION_FAILED);
                    assertThat(exception.getMessage()).doesNotContain("Authorization");
                    assertThat(exception.getMessage()).doesNotContain("sk-secret");
                    assertThat(exception.getMessage()).doesNotContain("用户完整问题");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void streamShouldMap503UnavailableSignalToProviderUnavailable() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.error(new IllegalStateException("connect refused 503 unavailable")),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.stream("invocation-stream-unavailable", request(ModelCallMode.STREAM))
                .collectList()
                .block(), ModelGatewayErrorCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    void callShouldMapUnexpectedExceptionToProviderUnavailable() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> {
                    throw new IllegalStateException("unexpected provider failure");
                },
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.call("invocation-unexpected", request(ModelCallMode.BLOCK)),
                ModelGatewayErrorCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    void streamShouldPropagateExistingModelGatewayException() {
        ModelGatewayException expectedException = new ModelGatewayException(ModelGatewayErrorCode.RESPONSE_INVALID);
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.error(expectedException),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertThatThrownBy(() -> provider.stream("invocation-stream-existing-exception", request(ModelCallMode.STREAM))
                .collectList()
                .block())
                .isSameAs(expectedException)
                .isInstanceOfSatisfying(ModelGatewayException.class, exception ->
                        assertThat(exception.getGatewayErrorCode()).isEqualTo(ModelGatewayErrorCode.RESPONSE_INVALID));
    }

    @Test
    void streamShouldFilterBlankChunkBeforeValidChunk() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("不会调用", 0, 0, 0),
                messages -> Flux.just(
                        chatResponse("   ", 1, 2, 3),
                        chatResponse("有效增量", 3, 5, 8)
                ),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        List<GatewayChunk> chunks = provider.stream("invocation-blank-before-valid", request(ModelCallMode.STREAM))
                .collectList()
                .block();

        assertThat(chunks).hasSize(2);
        assertTextChunk(chunks.get(0), "invocation-blank-before-valid", "有效增量");
        GatewayChunk finishedChunk = chunks.get(1);
        assertThat(finishedChunk.finished()).isTrue();
        assertThat(finishedChunk.usage()).isEqualTo(new ModelUsage(3, 5, 8));
    }

    @Test
    void streamShouldKeepModeMismatchIllegalArgumentException() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("模型回答", 0, 0, 0),
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertThatThrownBy(() -> provider.stream("invocation-mode", request(ModelCallMode.BLOCK)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void callShouldFallbackZeroUsageWhenMetadataMissing() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> new ChatResponse(List.of(new Generation(new AssistantMessage("模型回答")))),
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        GatewayResult result = provider.call("invocation-missing-usage", request(ModelCallMode.BLOCK));

        assertThat(result.usage()).isEqualTo(new ModelUsage(0, 0, 0));
    }

    @Test
    void callShouldFallbackUnknownRouteWhenSnapshotMissing() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("模型回答", 0, 0, 0),
                messages -> Flux.empty(),
                Optional::empty
        );

        GatewayResult result = provider.call("invocation-missing-route", request(ModelCallMode.BLOCK));

        assertThat(result.route().provider()).isEqualTo("unknown");
        assertThat(result.route().model()).isEqualTo("unknown");
    }

    @Test
    void getActiveChatConfigSnapshotShouldReturnEmptyBeforeChatClientInitialized() {
        ModelConfigEntity activeConfig = new ModelConfigEntity();
        activeConfig.setProvider("openai");
        activeConfig.setModelName("gpt-4o-mini");
        activeConfig.setModelType(ModelType.CHAT.getCode());
        activeConfig.setBaseUrl("https://api.example.com");
        activeConfig.setApiKey("sk-secret");
        activeConfig.setProxyPassword("proxy-secret");
        activeConfig.setProxyUsername("proxy-user");
        activeConfig.setProxyHost("proxy.example.com");
        activeConfig.setProxyPort(8080);
        ModelConfigQueryService queryService = mock(ModelConfigQueryService.class);
        when(queryService.getActiveConfig(ModelType.CHAT)).thenReturn(Optional.of(activeConfig));

        AiModelRegistry registry = new AiModelRegistry(mock(DynamicModelFactory.class), queryService);

        Optional<ModelConfigEntity> snapshot = registry.getActiveChatConfigSnapshot();

        assertThat(snapshot).isEmpty();
    }

    @Test
    void getActiveChatConfigSnapshotShouldFollowCachedChatClientAndAvoidExtraDbQuery() {
        ModelConfigEntity oldConfig = new ModelConfigEntity();
        oldConfig.setProvider("old-provider");
        oldConfig.setModelName("old-model");
        oldConfig.setModelType(ModelType.CHAT.getCode());
        oldConfig.setBaseUrl("https://old.example.com");
        oldConfig.setApiKey("sk-old-secret");
        oldConfig.setProxyPassword("proxy-old-secret");
        oldConfig.setProxyUsername("proxy-user");
        oldConfig.setProxyHost("proxy.example.com");
        oldConfig.setProxyPort(8080);
        ModelConfigEntity newConfig = new ModelConfigEntity();
        newConfig.setProvider("new-provider");
        newConfig.setModelName("new-model");
        newConfig.setModelType(ModelType.CHAT.getCode());
        newConfig.setBaseUrl("https://new.example.com");
        newConfig.setApiKey("sk-new-secret");
        DynamicModelFactory modelFactory = mock(DynamicModelFactory.class);
        ModelConfigQueryService queryService = mock(ModelConfigQueryService.class);
        when(queryService.getActiveConfig(ModelType.CHAT))
                .thenReturn(Optional.of(oldConfig))
                .thenReturn(Optional.of(newConfig));
        when(modelFactory.createChatModel(oldConfig)).thenReturn(mock(ChatModel.class));

        AiModelRegistry registry = new AiModelRegistry(modelFactory, queryService);

        registry.getChatClient();
        Optional<ModelConfigEntity> snapshot = registry.getActiveChatConfigSnapshot();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getProvider()).isEqualTo("old-provider");
        assertThat(snapshot.get().getModelName()).isEqualTo("old-model");
        assertThat(snapshot.get().getModelType()).isEqualTo(ModelType.CHAT.getCode());
        assertThat(snapshot.get().getBaseUrl()).isEqualTo("https://old.example.com");
        assertThat(snapshot.get().getApiKey()).isNull();
        assertThat(snapshot.get().getProxyPassword()).isNull();
        assertThat(snapshot.get().getProxyUsername()).isNull();
        assertThat(snapshot.get().getProxyHost()).isNull();
        assertThat(snapshot.get().getProxyPort()).isNull();
        verify(queryService, times(1)).getActiveConfig(ModelType.CHAT);
    }

    @Test
    void refreshChatShouldClearActiveChatConfigSnapshot() {
        ModelConfigEntity activeConfig = new ModelConfigEntity();
        activeConfig.setProvider("openai");
        activeConfig.setModelName("gpt-4o-mini");
        DynamicModelFactory modelFactory = mock(DynamicModelFactory.class);
        ModelConfigQueryService queryService = mock(ModelConfigQueryService.class);
        when(queryService.getActiveConfig(ModelType.CHAT)).thenReturn(Optional.of(activeConfig));
        when(modelFactory.createChatModel(activeConfig)).thenReturn(mock(ChatModel.class));
        AiModelRegistry registry = new AiModelRegistry(modelFactory, queryService);

        registry.getChatClient();
        registry.refreshChat();

        assertThat(registry.getActiveChatConfigSnapshot()).isEmpty();
    }

    @Test
    void callShouldKeepModeMismatchIllegalArgumentException() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("模型回答", 0, 0, 0),
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertThatThrownBy(() -> provider.call("invocation-mode", request(ModelCallMode.STREAM)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertGatewayException(Runnable runnable, ModelGatewayErrorCode expectedErrorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(ModelGatewayException.class, exception ->
                        assertThat(exception.getGatewayErrorCode()).isEqualTo(expectedErrorCode));
    }

    private static void assertTextChunk(GatewayChunk chunk, String invocationId, String content) {
        assertThat(chunk.invocationId()).isEqualTo(invocationId);
        assertThat(chunk.content()).isEqualTo(content);
        assertThat(chunk.finished()).isFalse();
        assertThat(chunk.usage()).isNull();
        assertThat(chunk.route()).isNull();
        assertThat(chunk.finishReason()).isNull();
    }

    private static Supplier<Optional<ModelConfigEntity>> safeRouteSupplier(String provider, String modelName) {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setProvider(provider);
        config.setModelName(modelName);
        return () -> Optional.of(config);
    }

    private static ModelGatewayRequest request(ModelCallMode mode) {
        List<ModelMessage> messages = List.of(
                new ModelMessage(ModelMessageRole.SYSTEM, "你是助手"),
                new ModelMessage(ModelMessageRole.USER, "你好")
        );
        return new ModelGatewayRequest("test-scene", ModelPrompt.direct(messages), mode,
                GatewayConstraints.defaults(), Map.of());
    }

    private static ChatResponse chatResponse(String text, int inputTokens, int outputTokens, int totalTokens) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(inputTokens, outputTokens, totalTokens))
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }
}
