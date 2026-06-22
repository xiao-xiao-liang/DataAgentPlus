package com.liang.data.agent.gateway.api;

/**
 * 模型最终路由信息，记录供应商、模型与降级结果。
 *
 * @param provider 供应商标识
 * @param model 模型标识
 * @param attemptCount 尝试次数
 * @param degraded 是否发生降级
 */
public record ModelRoute(String provider, String model, int attemptCount, boolean degraded) {
}
