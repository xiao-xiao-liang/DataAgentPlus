package com.liang.data.agent.config;

import com.liang.data.agent.gateway.context.GatewayExecutionContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型网关执行上下文配置，负责在应用入口提供工作流运行上下文工厂。
 *
 * <p>当前阶段尚未接入 Tracer，traceId 提供器先返回 null；真正的追踪编号来源由后续观测配置接入。</p>
 */
@Configuration
public class ModelGatewayContextConfiguration {

    /**
     * 创建模型网关执行上下文工厂。
     *
     * @return 模型网关执行上下文工厂
     */
    @Bean
    public GatewayExecutionContextFactory gatewayExecutionContextFactory() {
        // 1. 当前阶段不提前引入观测依赖，traceId 暂由后续观测配置提供。
        return new GatewayExecutionContextFactory(() -> null);
    }
}
