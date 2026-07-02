package com.liang.data.agent.gateway.response;

/**
 * 模型最终路由信息，记录供应商、模型与降级结果。
 *
 * @param provider 供应商标识
 * @param model 模型标识
 * @param attemptCount 尝试次数
 * @param degraded 是否发生降级
 */
public record ModelRoute(String provider, String model, int attemptCount, boolean degraded) {

    public ModelRoute {
        // 1. 校验供应商与模型标识，确保路由结果可追溯。
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("供应商标识不能为空");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        // 2. 校验尝试次数，至少应包含一次真实调用尝试。
        if (attemptCount <= 0) {
            throw new IllegalArgumentException("尝试次数必须大于零");
        }
    }
}
