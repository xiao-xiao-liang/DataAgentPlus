package com.liang.data.agent.gateway.context;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;
import java.util.function.Function;

/**
 * 模型网关 Reactor 上下文工具，用于写入和读取执行上下文。
 */
public final class GatewayReactorContext {

    private static final Object GATEWAY_EXECUTION_CONTEXT_KEY = GatewayExecutionContext.class;

    private GatewayReactorContext() {
    }

    /**
     * 创建写入模型网关执行上下文的 Reactor Context 转换函数。
     *
     * @param context 模型网关执行上下文
     * @return Reactor Context 转换函数
     */
    public static Function<Context, Context> with(GatewayExecutionContext context) {
        // 1. 拒绝写入空上下文，避免后续链路误判上下文已存在。
        Objects.requireNonNull(context, "执行上下文不能为空");
        // 2. 返回 contextWrite 可直接使用的写入函数。
        return reactorContext -> reactorContext.put(GATEWAY_EXECUTION_CONTEXT_KEY, context);
    }

    /**
     * 从 Reactor Context 中读取模型网关执行上下文。
     *
     * @param contextView Reactor Context 视图
     * @return 存在时返回执行上下文，不存在时返回空 Mono
     */
    public static Mono<GatewayExecutionContext> current(ContextView contextView) {
        // 1. 校验上下文视图，避免调用方传入空引用。
        Objects.requireNonNull(contextView, "Reactor上下文视图不能为空");
        // 2. 上下文不存在时返回空 Mono，不打断可选链路。
        return contextView.getOrEmpty(GATEWAY_EXECUTION_CONTEXT_KEY)
                .map(GatewayExecutionContext.class::cast)
                .map(Mono::just)
                .orElseGet(Mono::empty);
    }

    /**
     * 从 Reactor Context 中读取必需的模型网关执行上下文。
     *
     * @param contextView Reactor Context 视图
     * @return 模型网关执行上下文
     */
    public static GatewayExecutionContext currentOrThrow(ContextView contextView) {
        // 1. 校验上下文视图，避免调用方传入空引用。
        Objects.requireNonNull(contextView, "Reactor上下文视图不能为空");
        // 2. 上下文存在时直接返回，缺失时抛出明确中文异常。
        return contextView.getOrEmpty(GATEWAY_EXECUTION_CONTEXT_KEY)
                .map(GatewayExecutionContext.class::cast)
                .orElseThrow(() -> new IllegalStateException("模型网关执行上下文不存在"));
    }
}
