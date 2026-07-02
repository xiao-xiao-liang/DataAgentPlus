package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.enums.LlmServiceMode;
import com.liang.data.agent.gateway.response.GatewayChunk;
import com.liang.data.agent.gateway.request.GatewayConstraints;
import com.liang.data.agent.gateway.request.ModelCallMode;
import com.liang.data.agent.gateway.api.ModelGateway;
import com.liang.data.agent.gateway.request.ModelGatewayRequest;
import com.liang.data.agent.gateway.constants.ModelGatewayConstant;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import com.liang.data.agent.gateway.error.ModelGatewayException;
import com.liang.data.agent.gateway.prompt.ModelMessage;
import com.liang.data.agent.gateway.prompt.ModelMessageRole;
import com.liang.data.agent.gateway.prompt.ModelPrompt;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于模型网关的 LLM 服务适配器，负责将旧 LlmService 入口统一转换为模型网关请求。
 */
public class GatewayBackedLlmService implements LlmService {

    private final ModelGateway modelGateway;

    private final DataAgentProperties properties;

    /**
     * 创建基于模型网关的 LLM 服务适配器。
     *
     * @param modelGateway 模型网关
     * @param properties DataAgent 配置属性
     */
    public GatewayBackedLlmService(ModelGateway modelGateway, DataAgentProperties properties) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "模型网关不能为空");
        this.properties = Objects.requireNonNull(properties, "DataAgent配置不能为空");
    }

    @Override
    public Flux<ChatResponse> call(String system, String user) {
        return call(ModelGatewayConstant.LEGACY_SYSTEM_USER, system, user);
    }

    @Override
    public Flux<ChatResponse> call(String sceneCode, String system, String user) {
        // 1. 将系统消息和用户消息按顺序转换为网关直传消息。
        List<ModelMessage> messages = List.of(
                new ModelMessage(ModelMessageRole.SYSTEM, system),
                new ModelMessage(ModelMessageRole.USER, user));
        // 2. 使用统一网关请求执行模型调用。
        return callGateway(sceneCode, messages);
    }

    @Override
    public Flux<ChatResponse> callSystem(String system) {
        return callSystem(ModelGatewayConstant.LEGACY_SYSTEM_ONLY, system);
    }

    @Override
    public Flux<ChatResponse> callSystem(String sceneCode, String system) {
        // 1. 将旧系统消息入口映射为单条 SYSTEM 消息。
        List<ModelMessage> messages = List.of(new ModelMessage(ModelMessageRole.SYSTEM, system));
        // 2. 使用统一网关请求执行模型调用。
        return callGateway(sceneCode, messages);
    }

    @Override
    public Flux<ChatResponse> callUser(String user) {
        return callUser(ModelGatewayConstant.LEGACY_USER_ONLY, user);
    }

    @Override
    public Flux<ChatResponse> callUser(String sceneCode, String user) {
        // 1. 将旧用户消息入口映射为单条 USER 消息。
        List<ModelMessage> messages = List.of(new ModelMessage(ModelMessageRole.USER, user));
        // 2. 使用统一网关请求执行模型调用。
        return callGateway(sceneCode, messages);
    }

    private Flux<ChatResponse> callGateway(String sceneCode, List<ModelMessage> messages) {
        // 1. 根据既有 LLM 模式配置决定网关调用模式。
        ModelCallMode mode = resolveMode();
        // 2. 构造直传 Prompt 请求，阶段 2 不附加动态路由、预算或降级策略。
        ModelGatewayRequest request = new ModelGatewayRequest(sceneCode, ModelPrompt.direct(messages), mode,
                GatewayConstraints.defaults(), Map.of());
        if (ModelCallMode.STREAM == mode) {
            // 3. 流式模式过滤完成片段，仅将普通内容片段转换为 ChatResponse。
            return modelGateway.stream(request)
                    .filter(chunk -> !chunk.finished())
                    .map(GatewayChunk::content)
                    .map(ChatResponseUtil::createPureResponse)
                    .onErrorResume(this::resumeEmptyWhenInvalidResponse);
        }
        // 4. 非流式模式将网关聚合结果转换为单个 ChatResponse。
        return modelGateway.call(request)
                .map(result -> ChatResponseUtil.createPureResponse(result.content()))
                .flux();
    }

    private ModelCallMode resolveMode() {
        // 1. 复用既有 LlmService Bean 的配置语义，STREAM 配置走流式网关入口。
        if (LlmServiceMode.STREAM == properties.getLlmServiceMode()) {
            return ModelCallMode.STREAM;
        }
        // 2. 其他配置或空配置统一按 BLOCK 处理，避免意外进入流式调用。
        return ModelCallMode.BLOCK;
    }

    private Flux<ChatResponse> resumeEmptyWhenInvalidResponse(Throwable throwable) {
        // 1. 空响应类错误交给上层节点的空响应重试逻辑处理。
        if (throwable instanceof ModelGatewayException exception
                && ModelGatewayErrorCode.RESPONSE_INVALID == exception.getGatewayErrorCode()) {
            return Flux.empty();
        }
        // 2. 非空响应类错误继续向上传递，避免吞掉超时、认证失败等真实故障。
        return Flux.error(throwable);
    }
}
