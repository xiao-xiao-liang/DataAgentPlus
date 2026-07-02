package com.liang.data.agent.gateway.api;

import com.liang.data.agent.gateway.request.ModelGatewayRequest;
import com.liang.data.agent.gateway.response.GatewayChunk;
import com.liang.data.agent.gateway.response.GatewayResult;
import com.liang.data.agent.gateway.request.ModelCallMode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 模型网关统一入口，为上层提供供应商无关的非流式聚合结果与流式调用协议。
 */
public interface ModelGateway {

    /**
     * 发起非流式聚合结果调用，不表示阻塞线程。
     *
     * <p>具体实现必须在入口调用 {@link ModelGatewayRequest#requireMode(ModelCallMode)}，
     * 且仅接收 {@link ModelCallMode#BLOCK} 请求。</p>
     *
     * @param request 模型网关请求
     * @return 单次调用结果
     */
    Mono<GatewayResult> call(ModelGatewayRequest request);

    /**
     * 发起流式模型调用。
     *
     * <p>具体实现必须在入口调用 {@link ModelGatewayRequest#requireMode(ModelCallMode)}，
     * 且仅接收 {@link ModelCallMode#STREAM} 请求。</p>
     *
     * @param request 模型网关请求
     * @return 调用片段流
     */
    Flux<GatewayChunk> stream(ModelGatewayRequest request);
}
