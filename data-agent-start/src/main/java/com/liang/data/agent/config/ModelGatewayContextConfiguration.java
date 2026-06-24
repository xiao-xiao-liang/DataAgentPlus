package com.liang.data.agent.config;

import com.liang.data.agent.gateway.context.GatewayExecutionContextFactory;
import com.liang.data.agent.gateway.context.TraceIdProvider;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型网关执行上下文配置，负责在应用入口提供工作流运行上下文工厂和链路追踪编号提供器。
 *
 * <p>当应用接入 Micrometer Tracing 后，默认从当前 Span 读取 traceId；没有追踪上下文时返回 null。</p>
 */
@Configuration
public class ModelGatewayContextConfiguration {

    /**
     * 创建默认链路追踪编号提供器。
     *
     * @param tracerProvider Micrometer Tracer 提供器
     * @return 链路追踪编号提供器
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdProvider.class)
    public TraceIdProvider traceIdProvider(ObjectProvider<Tracer> tracerProvider) {
        return () -> {
            // 1. 获取当前 Tracer，未启用链路追踪时直接返回 null。
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer == null) {
                return null;
            }

            // 2. 获取当前 Span，非请求链路或未创建 Span 时直接返回 null。
            Span currentSpan = tracer.currentSpan();
            if (currentSpan == null) {
                return null;
            }

            // 3. 返回当前 Span 的 traceId，调用方负责按业务需要处理空值。
            return currentSpan.context().traceId();
        };
    }

    /**
     * 创建模型网关执行上下文工厂。
     *
     * @param traceIdProvider 链路追踪编号提供器
     * @return 模型网关执行上下文工厂
     */
    @Bean
    @ConditionalOnMissingBean(GatewayExecutionContextFactory.class)
    public GatewayExecutionContextFactory gatewayExecutionContextFactory(TraceIdProvider traceIdProvider) {
        // 1. 注入可替换的 traceId 提供器，后续观测配置可覆盖默认实现。
        return new GatewayExecutionContextFactory(traceIdProvider);
    }
}
