package com.liang.data.agent.gateway.response;

import java.util.Objects;

/**
 * 模型网关非流式聚合调用结果，汇总内容、用量、路由与结束原因。
 *
 * @param invocationId 调用标识
 * @param content 完整响应内容
 * @param usage Token 使用量
 * @param route 最终路由信息
 * @param finishReason 结束原因
 */
public record GatewayResult(String invocationId, String content, ModelUsage usage,
                            ModelRoute route, String finishReason) {

    public GatewayResult {
        // 1. 校验调用标识，确保结果可关联到一次网关调用。
        if (invocationId == null || invocationId.isBlank()) {
            throw new IllegalArgumentException("调用标识不能为空");
        }
        // 2. 校验聚合结果的必要字段，内容允许为空字符串但不能为 null。
        Objects.requireNonNull(content, "响应内容不能为空");
        Objects.requireNonNull(usage, "使用量不能为空");
        Objects.requireNonNull(route, "路由不能为空");
        if (finishReason == null || finishReason.isBlank()) {
            throw new IllegalArgumentException("结束原因不能为空");
        }
    }
}
