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

import java.util.Optional;

/**
 * AI 模型注册中心
 *
 * <p>核心职责:
 * <ul>
 *   <li>懒加载: 首次调用时从 DB 查配置并创建模型</li>
 *   <li>缓存: volatile + DCL 保证线程安全和可见性</li>
 *   <li>热切换: refreshChat()/refreshEmbedding() 置 null, 下次调用重新创建</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelRegistry {

    private final DynamicModelFactory modelFactory;
    private final ModelConfigQueryService queryService;

    private volatile ChatClient currentChatClient;
    private volatile ModelConfigEntity currentChatConfigSnapshot;
    private volatile EmbeddingModel currentEmbeddingModel;

    /**
     * 获取 ChatClient (懒加载 + 缓存)
     */
    public ChatClient getChatClient() {
        if (currentChatClient == null) {
            synchronized (this) {
                log.info("初始化全局 ChatClient...");
                if (currentChatClient == null) {
                    Optional<ModelConfigEntity> config = queryService.getActiveConfig(ModelType.CHAT);
                    if (config.isPresent()) {
                        ChatModel chatModel = modelFactory.createChatModel(config.get());
                        currentChatClient = ChatClient.builder(chatModel).build();
                        // 1. ChatClient 初始化成功后，同步记录与缓存客户端一致的脱敏配置快照。
                        currentChatConfigSnapshot = copySafeChatConfig(config.get());
                    }
                    if (currentChatClient == null) {
                        throw new ServiceException("未配置 CHAT 模型, 请在管理页面先配置对话模型", BaseErrorCode.SERVICE_ERROR);
                    }
                }
            }
        }
        return currentChatClient;
    }

    /**
     * 获取当前激活的对话模型脱敏配置快照。
     *
     * <p>仅返回 provider、modelName、modelType、baseUrl 等路由展示字段，不返回 apiKey、proxyPassword 等敏感配置。</p>
     *
     * @return 当前激活对话模型的脱敏配置快照
     */
    public Optional<ModelConfigEntity> getActiveChatConfigSnapshot() {
        // 1. 仅返回当前已加载 ChatClient 对应的快照，避免路由查询触发额外 DB 读取。
        return Optional.ofNullable(currentChatConfigSnapshot).map(this::copySafeChatConfig);
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
     * 获取 EmbeddingModel (懒加载 + DummyEmbeddingModel 兜底)
     */
    public EmbeddingModel getEmbeddingModel() {
        if (currentEmbeddingModel == null) {
            synchronized (this) {
                if (currentEmbeddingModel == null) {
                    log.info("初始化全局 EmbeddingModel...");
                    Optional<ModelConfigEntity> config = queryService.getActiveConfig(ModelType.EMBEDDING);
                    config.ifPresent(entity -> {
                        currentEmbeddingModel = modelFactory.createEmbeddingModel(entity);
                    });

                    // 兜底: 返回哑巴模型, 防止 VectorStore 启动崩溃
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
     * 清除 Chat 缓存, 下次调用将重新从 DB 加载
     */
    public void refreshChat() {
        this.currentChatClient = null;
        this.currentChatConfigSnapshot = null;
        log.info("ChatClient 缓存已清除, 下次调用将重新初始化");
    }

    /**
     * 清除 Embedding 缓存
     */
    public void refreshEmbedding() {
        this.currentEmbeddingModel = null;
        log.info("EmbeddingModel 缓存已清除, 下次调用将重新初始化");
    }
}
