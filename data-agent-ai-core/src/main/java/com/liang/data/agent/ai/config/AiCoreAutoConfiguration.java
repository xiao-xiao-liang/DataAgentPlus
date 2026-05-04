package com.liang.data.agent.ai.config;

import com.liang.data.agent.ai.llm.BlockLlmService;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.llm.StreamLlmService;
import com.liang.data.agent.ai.model.AiModelRegistry;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.enums.LlmServiceMode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 核心模块自动配置
 *
 * <p>注册 LlmService Bean, 根据配置决定使用 Stream 还是 Block 模式</p>
 *
 * <p>DynamicModelFactory / AiModelRegistry / ModelConfigQueryService
 * 都通过 @Service/@Component 自动扫描, 不在此处显式注册</p>
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(DataAgentProperties.class)
public class AiCoreAutoConfiguration {

    private final DataAgentProperties properties;

    /**
     * 根据 data-agent.llm-service-mode 决定注入哪个实现
     */
    @Bean
    public LlmService llmService(AiModelRegistry registry) {
        if (LlmServiceMode.STREAM == properties.getLlmServiceMode()) {
            return new StreamLlmService(registry);
        }
        return new BlockLlmService(registry);
    }

    /**
     * 注册一个代理 EmbeddingModel Bean，供 Spring AI 的 VectorStore 自动配置使用。
     * 实际调用会委托给 AiModelRegistry 中的动态模型。
     */
    @Bean
    public EmbeddingModel embeddingModel(AiModelRegistry registry) {
        return registry.getEmbeddingModel();
    }
}
