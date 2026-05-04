package com.liang.data.agent.ai.model;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.entity.ModelConfigEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 动态模型工厂 — 根据 DB 配置创建 ChatModel / EmbeddingModel
 *
 * <p>统一使用 OpenAiChatModel, 通过 baseUrl 实现多厂商兼容
 * (DeepSeek/通义/Kimi 等厂商均提供 OpenAI 兼容接口)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicModelFactory {

    /**
     * 创建 ChatModel 实例
     */
    public ChatModel createChatModel(ModelConfigEntity config) {
        log.info("创建 ChatModel 实例：provider={}, model={}, baseUrl={}",
                config.getProvider(), config.getModelName(), config.getBaseUrl());

        validateConfig(config);

        // 1. 构建 OpenAiApi
        String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(config.getBaseUrl());

        // 自定义 completions 路径 (某些厂商路径不同)
        if (StringUtils.hasText(config.getCompletionsPath())) {
            apiBuilder.completionsPath(config.getCompletionsPath());
        }
        OpenAiApi openAiApi = apiBuilder.build();

        // 2. 构建运行时选项
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(config.getModelName())
                .temperature(config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7d)
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : 4096)
                .streamUsage(true)
                .build();

        // 3. 返回 ChatModel
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();
    }

    /**
     * 创建 EmbeddingModel 实例
     */
    public EmbeddingModel createEmbeddingModel(ModelConfigEntity config) {
        log.info("创建 EmbeddingModel 实例：provider={}, model={}, baseUrl={}",
                config.getProvider(), config.getModelName(), config.getBaseUrl());

        validateConfig(config);

        String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(config.getBaseUrl());

        if (StringUtils.hasText(config.getEmbeddingsPath())) {
            apiBuilder.embeddingsPath(config.getEmbeddingsPath());
        }

        OpenAiApi openAiApi = apiBuilder.build();

        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(config.getModelName()).build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
    }

    private void validateConfig(ModelConfigEntity config) {
        if (!StringUtils.hasText(config.getBaseUrl())) {
            throw new ServiceException("baseUrl 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        if (!StringUtils.hasText(config.getModelName())) {
            throw new ServiceException("modelName 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        // custom 厂商可能不需要 apiKey
        if (!"custom".equalsIgnoreCase(config.getProvider()) && !StringUtils.hasText(config.getApiKey())) {
            throw new ServiceException("apiKey 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
    }
}
