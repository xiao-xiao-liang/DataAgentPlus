package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.ai.model.AiModelRegistry;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.dal.entity.ModelConfigEntity;
import com.liang.data.agent.gateway.api.GatewayChunk;
import com.liang.data.agent.gateway.api.GatewayResult;
import com.liang.data.agent.gateway.api.ModelCallMode;
import com.liang.data.agent.gateway.api.ModelGatewayRequest;
import com.liang.data.agent.gateway.api.ModelMessage;
import com.liang.data.agent.gateway.api.ModelMessageRole;
import com.liang.data.agent.gateway.api.ModelRoute;
import com.liang.data.agent.gateway.api.ModelUsage;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import com.liang.data.agent.gateway.error.ModelGatewayException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * OpenAI 兼容模型供应商适配器。
 *
 * <p>负责将模型网关请求转发给 Spring AI ChatClient，并把 Spring AI 响应统一转换为网关结果或流式片段。</p>
 */
@Component
public class OpenAiCompatibleGatewayProvider {

    private static final String UNKNOWN_ROUTE_VALUE = "unknown";

    private static final String DEFAULT_FINISH_REASON = "stop";

    private final ChatClientInvoker invoker;

    private final Supplier<Optional<ModelConfigEntity>> routeSupplier;

    /**
     * 创建生产环境 OpenAI 兼容模型供应商适配器。
     *
     * @param registry AI 模型注册中心
     */
    public OpenAiCompatibleGatewayProvider(AiModelRegistry registry) {
        this(new DefaultChatClientInvoker(registry), registry::getActiveChatConfigSnapshot);
    }

    OpenAiCompatibleGatewayProvider(ChatClientInvoker invoker, Supplier<Optional<ModelConfigEntity>> routeSupplier) {
        this.invoker = Objects.requireNonNull(invoker, "模型调用器不能为空");
        this.routeSupplier = Objects.requireNonNull(routeSupplier, "路由快照提供器不能为空");
    }

    OpenAiCompatibleGatewayProvider(Function<List<ModelMessage>, ChatResponse> callFunction,
                                    Function<List<ModelMessage>, Flux<ChatResponse>> streamFunction,
                                    Supplier<Optional<ModelConfigEntity>> routeSupplier) {
        this(new ChatClientInvoker() {
            @Override
            public ChatResponse call(List<ModelMessage> messages) {
                return callFunction.apply(messages);
            }

            @Override
            public Flux<ChatResponse> stream(List<ModelMessage> messages) {
                return streamFunction.apply(messages);
            }
        }, routeSupplier);
    }

    /**
     * 执行非流式模型调用。
     *
     * @param invocationId 调用标识
     * @param request 网关请求
     * @return 非流式模型调用结果
     */
    public GatewayResult call(String invocationId, ModelGatewayRequest request) {
        Objects.requireNonNull(request, "模型网关请求不能为空");
        request.requireMode(ModelCallMode.BLOCK);
        try {
            // 1. 仅处理直接消息，阶段 2 不负责模板解析。
            ChatResponse response = invoker.call(request.prompt().messages());
            // 2. 校验模型响应文本，避免向上游返回空白内容。
            String content = extractRequiredText(response);
            // 3. 汇总用量与脱敏路由信息，返回统一网关结果。
            return new GatewayResult(invocationId, content, extractUsage(response),
                    buildRoute(), DEFAULT_FINISH_REASON);
        } catch (ModelGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw convertException(exception);
        }
    }

    /**
     * 执行流式模型调用。
     *
     * @param invocationId 调用标识
     * @param request 网关请求
     * @return 流式模型调用片段
     */
    public Flux<GatewayChunk> stream(String invocationId, ModelGatewayRequest request) {
        Objects.requireNonNull(request, "模型网关请求不能为空");
        request.requireMode(ModelCallMode.STREAM);
        try {
            AtomicReference<ModelUsage> latestUsage = new AtomicReference<>(new ModelUsage(0, 0, 0));
            // 1. 获取上游流式响应，并过滤空白增量内容。
            return invoker.stream(request.prompt().messages())
                    .handle((response, sink) -> {
                        latestUsage.set(extractUsage(response));
                        String text = ChatResponseUtil.getText(response);
                        if (text != null && !text.isBlank()) {
                            sink.next(new GatewayChunk(invocationId, text, false, null, null, null));
                        }
                    })
                    // 2. 在流完成时补充最终片段，携带最终用量、路由和结束原因。
                    .cast(GatewayChunk.class)
                    .concatWith(Flux.defer(() -> Flux.just(new GatewayChunk(invocationId, "",
                            true, latestUsage.get(), buildRoute(), DEFAULT_FINISH_REASON))))
                    // 3. 统一转换上游异常，不暴露 Prompt、响应原文或密钥信息。
                    .onErrorMap(this::convertException);
        } catch (ModelGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw convertException(exception);
        }
    }

    private String extractRequiredText(ChatResponse response) {
        // 1. 检查响应对象是否为空。
        if (response == null) {
            throw new ModelGatewayException(ModelGatewayErrorCode.RESPONSE_INVALID);
        }
        // 2. 检查响应文本是否为空白。
        String content = ChatResponseUtil.getText(response);
        if (content == null || content.isBlank()) {
            throw new ModelGatewayException(ModelGatewayErrorCode.RESPONSE_INVALID);
        }
        return content;
    }

    private ModelRoute buildRoute() {
        // 1. 读取脱敏配置快照，缺失时使用 unknown 保持路由字段可追踪。
        Optional<ModelConfigEntity> configOptional = routeSupplier.get();
        String provider = configOptional.map(ModelConfigEntity::getProvider)
                .filter(value -> !value.isBlank())
                .orElse(UNKNOWN_ROUTE_VALUE);
        String modelName = configOptional.map(ModelConfigEntity::getModelName)
                .filter(value -> !value.isBlank())
                .orElse(UNKNOWN_ROUTE_VALUE);
        // 2. 单模型切换阶段没有降级链路，因此尝试次数固定为 1，degraded 固定为 false。
        return new ModelRoute(provider, modelName, 1, false);
    }

    private ModelUsage extractUsage(ChatResponse response) {
        try {
            // 1. 优先读取 Spring AI 元数据中的 usage。
            if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return new ModelUsage(0, 0, 0);
            }
            Usage usage = response.getMetadata().getUsage();
            long inputTokens = safeTokenCount(usage.getPromptTokens());
            long outputTokens = safeTokenCount(usage.getCompletionTokens());
            // 2. ModelUsage 要求 total=input+output，因此统一归一化总量。
            return new ModelUsage(inputTokens, outputTokens, inputTokens + outputTokens);
        } catch (RuntimeException exception) {
            return new ModelUsage(0, 0, 0);
        }
    }

    private long safeTokenCount(Integer tokenCount) {
        if (tokenCount == null || tokenCount < 0) {
            return 0L;
        }
        return tokenCount.longValue();
    }

    private ModelGatewayException convertException(Throwable throwable) {
        // 1. 已经是网关异常时原样透传。
        if (throwable instanceof ModelGatewayException exception) {
            return exception;
        }
        // 2. 根据异常类型和脱敏后的错误摘要映射结构化错误码。
        ModelGatewayErrorCode errorCode = resolveErrorCode(throwable);
        return new ModelGatewayException(errorCode, errorCode.message(), throwable);
    }

    private ModelGatewayErrorCode resolveErrorCode(Throwable throwable) {
        String message = normalizeMessage(throwable);
        if (hasCause(throwable, TimeoutException.class)
                || message.contains("timeout")
                || message.contains("timed out")
                || message.contains("超时")) {
            return ModelGatewayErrorCode.PROVIDER_TIMEOUT;
        }
        if (message.contains("401") || message.contains("403")) {
            return ModelGatewayErrorCode.AUTHENTICATION_FAILED;
        }
        if (message.contains("429")) {
            return ModelGatewayErrorCode.RATE_LIMITED;
        }
        if (message.matches(".*\\b5\\d\\d\\b.*")
                || message.contains("connect")
                || message.contains("connection")
                || message.contains("refused")
                || message.contains("unavailable")) {
            return ModelGatewayErrorCode.PROVIDER_UNAVAILABLE;
        }
        return ModelGatewayErrorCode.PROVIDER_UNAVAILABLE;
    }

    private String normalizeMessage(Throwable throwable) {
        // 1. 只读取异常消息用于分类，不记录、不拼接 Prompt 或响应正文。
        StringBuilder messageBuilder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                messageBuilder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return messageBuilder.toString().toLowerCase(Locale.ROOT);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        // 1. 沿异常链查找指定原因类型。
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 默认 Spring AI ChatClient 调用器。
     */
    private static class DefaultChatClientInvoker implements ChatClientInvoker {

        private final AiModelRegistry registry;

        DefaultChatClientInvoker(AiModelRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "AI模型注册中心不能为空");
        }

        @Override
        public ChatResponse call(List<ModelMessage> messages) {
            // 1. 构造 Spring AI 消息列表。
            List<Message> springMessages = convertMessages(messages);
            // 2. 调用 ChatClient 并返回原始 ChatResponse。
            return getChatClient().prompt().messages(springMessages).call().chatResponse();
        }

        @Override
        public Flux<ChatResponse> stream(List<ModelMessage> messages) {
            // 1. 构造 Spring AI 消息列表。
            List<Message> springMessages = convertMessages(messages);
            // 2. 调用 ChatClient 并返回原始流式 ChatResponse。
            return getChatClient().prompt().messages(springMessages).stream().chatResponse();
        }

        private ChatClient getChatClient() {
            return registry.getChatClient();
        }

        private List<Message> convertMessages(List<ModelMessage> messages) {
            // 1. 阶段 2 主要使用 system/user；assistant/tool 暂按 user 传递，避免直接消息调用失败。
            return messages.stream()
                    .map(message -> message.role() == ModelMessageRole.SYSTEM
                            ? new SystemMessage(message.content())
                            : new UserMessage(message.content()))
                    .map(Message.class::cast)
                    .toList();
        }
    }
}

/**
 * Spring AI ChatClient 调用抽象。
 */
interface ChatClientInvoker {

    /**
     * 执行非流式调用。
     *
     * @param messages 直接消息列表
     * @return Spring AI 对话响应
     */
    ChatResponse call(List<ModelMessage> messages);

    /**
     * 执行流式调用。
     *
     * @param messages 直接消息列表
     * @return Spring AI 对话响应流
     */
    Flux<ChatResponse> stream(List<ModelMessage> messages);
}
