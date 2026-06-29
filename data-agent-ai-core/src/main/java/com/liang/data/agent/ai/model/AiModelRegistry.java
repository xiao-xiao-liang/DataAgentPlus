package com.liang.data.agent.ai.model;

import com.liang.data.agent.common.enums.ModelType;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.entity.ModelConfigEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * AI 模型注册中心。
 *
 * <p>负责按需加载并缓存对话模型与向量模型，支持刷新后重新加载。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelRegistry {

    private final DynamicModelFactory modelFactory;
    private final ModelConfigQueryService queryService;

    private volatile ChatClientRouteSnapshot currentChatSnapshot;
    private volatile EmbeddingModel currentEmbeddingModel;

    /**
     * 对话模型客户端与其脱敏路由配置快照。
     *
     * @param chatClient 对话模型客户端
     * @param routeConfig 脱敏路由配置
     */
    public record ChatClientRouteSnapshot(ChatClient chatClient, ModelConfigEntity routeConfig) {

        /**
         * 校验快照字段，避免发布不完整的客户端路由状态。
         */
        public ChatClientRouteSnapshot {
            Objects.requireNonNull(chatClient, "对话模型客户端不能为空");
            Objects.requireNonNull(routeConfig, "脱敏路由配置不能为空");
        }
    }

    /**
     * 获取 ChatClient。
     *
     * @return 当前对话模型客户端
     */
    public ChatClient getChatClient() {
        // 1. 通过同一个 holder 返回客户端，避免末尾再次读取 volatile 字段时被 refresh 清空。
        return getChatClientRouteSnapshot().chatClient();
    }

    /**
     * 获取 ChatClient 与对应脱敏路由配置快照。
     *
     * @return ChatClient 与路由配置的不可变快照
     */
    public ChatClientRouteSnapshot getChatClientRouteSnapshot() {
        ChatClientRouteSnapshot snapshot = currentChatSnapshot;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = currentChatSnapshot;
                if (snapshot == null) {
                    log.info("初始化全局 ChatClient...");
                    // 1. 查询当前启用的对话模型配置。
                    Optional<ModelConfigEntity> config = queryService.getActiveConfig(ModelType.CHAT);
                    if (config.isEmpty()) {
                        throw new ServiceException("未配置 CHAT 模型，请在管理页面先配置对话模型",
                                BaseErrorCode.SERVICE_ERROR);
                    }
                    // 2. 基于同一份配置同时构造 ChatClient 与脱敏路由快照。
                    ModelConfigEntity configEntity = config.get();
                    ChatModel chatModel = modelFactory.createChatModel(configEntity);
                    ChatClient chatClient = ChatClient.builder(chatModel).build();
                    ModelConfigEntity routeConfig = copySafeChatConfig(configEntity);
                    // 3. 一次性发布 holder，保证调用客户端与展示路由来自同一份配置。
                    snapshot = new ChatClientRouteSnapshot(chatClient, routeConfig);
                    currentChatSnapshot = snapshot;
                }
            }
        }
        return snapshot;
    }

    /**
     * 获取当前激活的对话模型脱敏配置快照。
     *
     * <p>仅返回 provider、modelName、modelType、baseUrl 等路由展示字段，不返回 apiKey、proxyPassword 等敏感配置。</p>
     *
     * @return 当前激活对话模型的脱敏配置快照
     */
    public Optional<ModelConfigEntity> getActiveChatConfigSnapshot() {
        // 1. 仅返回当前已发布 holder 中的脱敏路由配置，避免额外读取数据库。
        return Optional.ofNullable(currentChatSnapshot)
                .map(ChatClientRouteSnapshot::routeConfig)
                .map(this::copySafeChatConfig);
    }

    private ModelConfigEntity copySafeChatConfig(ModelConfigEntity source) {
        // 1. 仅复制路由展示需要的非敏感字段。
        ModelConfigEntity snapshot = new ModelConfigEntity();
        snapshot.setProvider(source.getProvider());
        snapshot.setModelName(source.getModelName());
        snapshot.setModelType(source.getModelType());
        snapshot.setBaseUrl(source.getBaseUrl());
        // 2. 不复制 apiKey、proxyPassword、代理地址等敏感或连接配置。
        return snapshot;
    }

    /**
     * 获取 EmbeddingModel。
     *
     * @return 向量模型，未配置时返回兜底模型
     */
    public EmbeddingModel getEmbeddingModel() {
        if (currentEmbeddingModel == null) {
            synchronized (this) {
                if (currentEmbeddingModel == null) {
                    log.info("初始化全局 EmbeddingModel...");
                    Optional<ModelConfigEntity> config = queryService.getActiveConfig(ModelType.EMBEDDING);
                    config.ifPresent(entity -> currentEmbeddingModel = modelFactory.createEmbeddingModel(entity));

                    // 1. 未配置向量模型时返回兜底模型，防止 VectorStore 启动失败。
                    if (currentEmbeddingModel == null) {
                        log.warn("使用 DummyEmbeddingModel 兜底");
                        currentEmbeddingModel = new DummyEmbeddingModel();
                    }
                }
            }
        }
        return currentEmbeddingModel;
    }

    /**
     * 清除 Chat 缓存，下一次调用将重新从数据库加载。
     */
    public void refreshChat() {
        synchronized (this) {
            // 1. 在同一把锁内清空 holder，下一次调用会重新构建客户端与路由快照。
            this.currentChatSnapshot = null;
        }
        log.info("ChatClient 缓存已清除，下一次调用将重新初始化");
    }

    /**
     * 清除 Embedding 缓存。
     */
    public void refreshEmbedding() {
        this.currentEmbeddingModel = null;
        log.info("EmbeddingModel 缓存已清除，下一次调用将重新初始化");
    }
}
