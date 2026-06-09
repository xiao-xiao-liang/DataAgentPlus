package com.liang.data.agent.service.ratelimit;

import com.liang.data.agent.ai.llm.LlmService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 资源门控配置类。
 *
 * <p>为大模型服务注册主装饰器，使业务节点无需逐个感知资源门控。</p>
 */
@Configuration
public class ResourceGateConfiguration {

    /**
     * 注册受资源门控保护的大模型服务。
     *
     * @param delegate     原始大模型服务
     * @param resourceGate 资源门控
     * @return 受控大模型服务
     */
    @Bean
    @Primary
    public LlmService resourceGatedLlmService(@Qualifier("llmService") LlmService delegate,
                                              ResourceGate resourceGate) {
        return new ResourceGatedLlmService(delegate, resourceGate);
    }
}
