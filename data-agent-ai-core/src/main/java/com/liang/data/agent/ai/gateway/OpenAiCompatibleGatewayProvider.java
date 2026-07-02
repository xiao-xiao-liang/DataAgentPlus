package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.ai.model.AiModelRegistry;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.dal.entity.ModelConfigEntity;
import com.liang.data.agent.gateway.response.GatewayChunk;
import com.liang.data.agent.gateway.response.GatewayResult;
import com.liang.data.agent.gateway.request.ModelCallMode;
import com.liang.data.agent.gateway.request.ModelGatewayRequest;
import com.liang.data.agent.gateway.prompt.ModelMessage;
import com.liang.data.agent.gateway.prompt.ModelMessageRole;
import com.liang.data.agent.gateway.response.ModelRoute;
import com.liang.data.agent.gateway.response.ModelUsage;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import com.liang.data.agent.gateway.error.ModelGatewayException;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /**
     * 创建生产环境 OpenAI 兼容模型供应商适配器。
     *
     * @param registry AI 模型注册中心
     */
    @Autowired
    public OpenAiCompatibleGatewayProvider(AiModelRegistry registry) {
        this(new DefaultChatClientInvoker(registry));
    }

    OpenAiCompatibleGatewayProvider(ChatClientInvoker invoker) {
        this.invoker = Objects.requireNonNull(invoker, "模型调用器不能为空");
    }

    OpenAiCompatibleGatewayProvider(ChatClientInvoker invoker, Supplier<Optional<ModelConfigEntity>> routeSupplier) {
        this(wrapInvoker(invoker, routeSupplier));
    }

    OpenAiCompatibleGatewayProvider(Function<List<ModelMessage>, ChatResponse> callFunction,
                                    Function<List<ModelMessage>, Flux<ChatResponse>> streamFunction,
                                    Supplier<Optional<ModelConfigEntity>> routeSupplier) {
        this(wrapFunctions(callFunction, streamFunction, routeSupplier));
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
            // 1. 获取本次调用的响应与路由快照，保证 route 绑定本次实际调用。
            ChatClientCallResult callResult = invoker.call(request.prompt().messages());
            ChatResponse response = callResult.response();
            // 2. 校验模型响应文本，避免向上游返回空白内容。
            String content = extractRequiredText(response);
            // 3. 汇总用量与脱敏路由信息，返回统一网关结果。
            return new GatewayResult(invocationId, content, extractUsage(response),
                    buildRoute(callResult.routeConfig()), DEFAULT_FINISH_REASON);
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
            AtomicBoolean emittedText = new AtomicBoolean(false);
            // 1. 获取本次流式调用的响应流与路由快照，后续完成片段复用同一个 route。
            ChatClientStreamResult streamResult = invoker.stream(request.prompt().messages());
            ModelRoute route = buildRoute(streamResult.routeConfig());
            return streamResult.responses()
                    .handle((response, sink) -> {
                        // 1. null 响应属于上游格式错误，需要立即终止流。
                        if (response == null) {
                            sink.error(new ModelGatewayException(ModelGatewayErrorCode.RESPONSE_INVALID));
                            return;
                        }
                        // 2. 空白增量属于兼容性噪声，过滤后等待后续有效文本。
                        String text = ChatResponseUtil.getText(response);
                        if (text.isBlank()) {
                            return;
                        }
                        // 3. 仅在有效文本片段出现后标记流有效，并记录最新用量。
                        emittedText.set(true);
                        latestUsage.set(extractUsage(response));
                        sink.next(new GatewayChunk(invocationId, text, false, null, null, null));
                    })
                    // 2. 在流完成时校验至少已输出一个有效文本，再补充最终片段。
                    .cast(GatewayChunk.class)
                    .concatWith(Flux.defer(() -> {
                        if (!emittedText.get()) {
                            return Flux.error(new ModelGatewayException(ModelGatewayErrorCode.RESPONSE_INVALID));
                        }
                        return Flux.just(new GatewayChunk(invocationId, "",
                                true, latestUsage.get(), route, DEFAULT_FINISH_REASON));
                    }))
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
        if (content.isBlank()) {
            throw new ModelGatewayException(ModelGatewayErrorCode.RESPONSE_INVALID);
        }
        return content;
    }

    private ModelRoute buildRoute(Optional<ModelConfigEntity> configOptional) {
        // 1. 使用本次调用捕获的脱敏配置快照，缺失时使用 unknown 保持路由字段可追踪。
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
            if (response == null || response.getMetadata().getUsage() == null) {
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
        return new ModelGatewayException(errorCode, errorCode.message());
    }

    private ModelGatewayErrorCode resolveErrorCode(Throwable throwable) {
        String message = normalizeMessage(throwable);
        if (hasCause(throwable)
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

    private boolean hasCause(Throwable throwable) {
        // 1. 沿异常链查找指定原因类型。
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ChatClientInvoker wrapFunctions(Function<List<ModelMessage>, ChatResponse> callFunction,
                                                   Function<List<ModelMessage>, Flux<ChatResponse>> streamFunction,
                                                   Supplier<Optional<ModelConfigEntity>> routeSupplier) {
        Objects.requireNonNull(callFunction, "非流式调用函数不能为空");
        Objects.requireNonNull(streamFunction, "流式调用函数不能为空");
        return wrapInvoker(new ChatClientInvoker() {
            @Override
            public ChatClientCallResult call(List<ModelMessage> messages) {
                return new ChatClientCallResult(callFunction.apply(messages), Optional.empty());
            }

            @Override
            public ChatClientStreamResult stream(List<ModelMessage> messages) {
                return new ChatClientStreamResult(streamFunction.apply(messages), Optional.empty());
            }
        }, routeSupplier);
    }

    private static ChatClientInvoker wrapInvoker(ChatClientInvoker invoker,
                                                Supplier<Optional<ModelConfigEntity>> routeSupplier) {
        Objects.requireNonNull(invoker, "模型调用器不能为空");
        Objects.requireNonNull(routeSupplier, "路由快照提供器不能为空");
        return new ChatClientInvoker() {
            @Override
            public ChatClientCallResult call(List<ModelMessage> messages) {
                // 1. 在调用前捕获测试场景路由，避免调用后 routeSupplier 已发生变化。
                Optional<ModelConfigEntity> routeConfig = routeSupplier.get();
                ChatClientCallResult result = invoker.call(messages);
                return new ChatClientCallResult(result.response(), routeConfig);
            }

            @Override
            public ChatClientStreamResult stream(List<ModelMessage> messages) {
                // 1. 在创建流前捕获测试场景路由，保证最终片段使用本次调用 route。
                Optional<ModelConfigEntity> routeConfig = routeSupplier.get();
                ChatClientStreamResult result = invoker.stream(messages);
                return new ChatClientStreamResult(result.responses(), routeConfig);
            }
        };
    }

    /**
         * 默认 Spring AI ChatClient 调用器。
         */
        private record DefaultChatClientInvoker(AiModelRegistry registry) implements ChatClientInvoker {

            private DefaultChatClientInvoker(AiModelRegistry registry) {
                this.registry = Objects.requireNonNull(registry, "AI 模型注册中心不能为空");
            }

            @Override
            public ChatClientCallResult call(List<ModelMessage> messages) {
                // 1. 在调用开始时获取 ChatClient 与路由的同一个快照。
                AiModelRegistry.ChatClientRouteSnapshot snapshot = registry.getChatClientRouteSnapshot();
                // 2. 构造 Spring AI 消息列表。
                List<Message> springMessages = convertMessages(messages);
                // 3. 使用快照中的 ChatClient 发起调用，并返回同一快照中的路由。
                ChatResponse response = snapshot.chatClient().prompt().messages(springMessages).call().chatResponse();
                return new ChatClientCallResult(response, Optional.of(snapshot.routeConfig()));
            }

            @Override
            public ChatClientStreamResult stream(List<ModelMessage> messages) {
                // 1. 在调用开始时获取 ChatClient 与路由的同一个快照。
                AiModelRegistry.ChatClientRouteSnapshot snapshot = registry.getChatClientRouteSnapshot();
                // 2. 构造 Spring AI 消息列表。
                List<Message> springMessages = convertMessages(messages);
                // 3. 使用快照中的 ChatClient 创建响应流，并返回同一快照中的路由。
                Flux<ChatResponse> responses = snapshot.chatClient().prompt().messages(springMessages).stream().chatResponse();
                return new ChatClientStreamResult(responses, Optional.of(snapshot.routeConfig()));
            }

            private List<Message> convertMessages(List<ModelMessage> messages) {
                // 1. 阶段 2 主要使用 system/user，assistant/tool 暂按 user 传递，避免直接消息调用失败。
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
     * @return 对话响应与本次调用路由
     */
    ChatClientCallResult call(List<ModelMessage> messages);

    /**
     * 执行流式调用。
     *
     * @param messages 直接消息列表
     * @return 对话响应流与本次调用路由
     */
    ChatClientStreamResult stream(List<ModelMessage> messages);
}

/**
 * 非流式调用响应与本次调用路由。
 *
 * @param response Spring AI 对话响应
 * @param routeConfig 本次调用使用的脱敏路由配置
 */
record ChatClientCallResult(ChatResponse response, Optional<ModelConfigEntity> routeConfig) {
}

/**
 * 流式调用响应与本次调用路由。
 *
 * @param responses Spring AI 对话响应流
 * @param routeConfig 本次调用使用的脱敏路由配置
 */
record ChatClientStreamResult(Flux<ChatResponse> responses, Optional<ModelConfigEntity> routeConfig) {

    /**
     * 校验响应流并归一化空路由，避免调用方处理 null。
     */
    ChatClientStreamResult {
        Objects.requireNonNull(responses, "对话响应流不能为空");
    }
}
