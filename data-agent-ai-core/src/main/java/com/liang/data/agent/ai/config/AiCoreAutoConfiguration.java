package com.liang.data.agent.ai.config;

import com.liang.data.agent.ai.gateway.DefaultModelGateway;
import com.liang.data.agent.ai.gateway.GatewayBackedLlmService;
import com.liang.data.agent.ai.gateway.ModelGatewayInvocationRecorder;
import com.liang.data.agent.ai.gateway.OpenAiCompatibleGatewayProvider;
import com.liang.data.agent.ai.gateway.PersistentModelGatewayInvocationRecorder;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.model.AiModelRegistry;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.dal.mapper.ModelGatewayAttemptMapper;
import com.liang.data.agent.dal.mapper.ModelGatewayInvocationMapper;
import com.liang.data.agent.gateway.api.ModelGateway;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 核心模块自动配置
 *
 * <p>注册模型网关、模型网关记录器和默认 LlmService Bean。</p>
 *
 * <p>DynamicModelFactory / AiModelRegistry / ModelConfigQueryService
 * 都通过 @Service/@Component 自动扫描, 不在此处显式注册</p>
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(DataAgentProperties.class)
public class AiCoreAutoConfiguration {

    private final DataAgentProperties properties;

    @Bean
    @ConditionalOnMissingBean
    public OpenAiCompatibleGatewayProvider openAiCompatibleGatewayProvider(AiModelRegistry registry) {
        return new OpenAiCompatibleGatewayProvider(registry);
    }

    /**
     * 注册模型网关调用记录器。
     *
     * @param invocationMapper 模型网关主调用记录 Mapper
     * @param attemptMapper 模型网关尝试记录 Mapper
     * @return 模型网关调用记录器
     */
    @Bean
    @ConditionalOnMissingBean
    public ModelGatewayInvocationRecorder modelGatewayInvocationRecorder(
            ModelGatewayInvocationMapper invocationMapper,
            ModelGatewayAttemptMapper attemptMapper) {
        return new PersistentModelGatewayInvocationRecorder(invocationMapper, attemptMapper,
                properties.getModelGateway().isPersistenceEnabled());
    }

    /**
     * 注册默认模型网关。
     *
     * @param provider OpenAI 兼容模型 Provider
     * @param recorder 模型网关调用记录器
     * @param meterRegistryProvider 指标注册器提供者
     * @return 默认模型网关
     */
    @Bean
    @ConditionalOnMissingBean
    public ModelGateway modelGateway(OpenAiCompatibleGatewayProvider provider,
                                     ModelGatewayInvocationRecorder recorder,
                                     ObjectProvider<MeterRegistry> meterRegistryProvider) {
        Duration timeout = Duration.ofSeconds(properties.getModelGateway().getDefaultTimeoutSeconds());
        return new DefaultModelGateway(provider, recorder, timeout, meterRegistryProvider.getIfAvailable());
    }

    /**
     * 注册默认 LLM 服务，统一通过模型网关发起模型调用。
     *
     * @param modelGateway 模型网关
     * @return 基于模型网关的 LLM 服务
     */
    @Bean
    public LlmService llmService(ModelGateway modelGateway) {
        return new GatewayBackedLlmService(modelGateway, properties);
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
