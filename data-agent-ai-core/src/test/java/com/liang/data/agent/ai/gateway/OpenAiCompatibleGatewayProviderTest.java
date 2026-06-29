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
    void callShouldMap401Or403MessageToAuthenticationFailed() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> {
                    throw new IllegalStateException("HTTP 401 unauthorized");
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
    void callShouldMapNullOrBlankResponseTextToResponseInvalid() {
        OpenAiCompatibleGatewayProvider provider = new OpenAiCompatibleGatewayProvider(
                messages -> chatResponse("   ", 0, 0, 0),
                messages -> Flux.empty(),
                safeRouteSupplier("openai", "gpt-4o-mini")
        );

        assertGatewayException(() -> provider.call("invocation-empty", request(ModelCallMode.BLOCK)),
                ModelGatewayErrorCode.RESPONSE_INVALID);
    }

    @Test
    void getActiveChatConfigSnapshotShouldNotExposeSensitiveFields() {
        ModelConfigEntity activeConfig = new ModelConfigEntity();
        activeConfig.setProvider("openai");
        activeConfig.setModelName("gpt-4o-mini");
        activeConfig.setModelType(ModelType.CHAT.getCode());
        activeConfig.setBaseUrl("https://api.example.com");
        activeConfig.setApiKey("sk-secret");
        activeConfig.setProxyPassword("proxy-secret");
        activeConfig.setProxyUsername("proxy-user");
        activeConfig.setProxyHost("proxy.example.com");
        ModelConfigQueryService queryService = mock(ModelConfigQueryService.class);
        when(queryService.getActiveConfig(ModelType.CHAT)).thenReturn(Optional.of(activeConfig));

        AiModelRegistry registry = new AiModelRegistry(mock(DynamicModelFactory.class), queryService);

        Optional<ModelConfigEntity> snapshot = registry.getActiveChatConfigSnapshot();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getProvider()).isEqualTo("openai");
        assertThat(snapshot.get().getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(snapshot.get().getModelType()).isEqualTo(ModelType.CHAT.getCode());
        assertThat(snapshot.get().getBaseUrl()).isEqualTo("https://api.example.com");
        assertThat(snapshot.get().getApiKey()).isNull();
        assertThat(snapshot.get().getProxyPassword()).isNull();
        assertThat(snapshot.get().getProxyUsername()).isNull();
        assertThat(snapshot.get().getProxyHost()).isNull();
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
