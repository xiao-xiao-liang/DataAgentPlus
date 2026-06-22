package com.liang.data.agent.gateway.api;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 模型网关统一入口，为上层提供供应商无关的阻塞与流式调用协议。
 */
public interface ModelGateway {

    /**
     * 发起阻塞模型调用。
     *
     * @param request 模型网关请求
     * @return 单次调用结果
     */
    Mono<GatewayResult> call(ModelGatewayRequest request);

    /**
     * 发起流式模型调用。
     *
     * @param request 模型网关请求
     * @return 调用片段流
     */
    Flux<GatewayChunk> stream(ModelGatewayRequest request);
}
