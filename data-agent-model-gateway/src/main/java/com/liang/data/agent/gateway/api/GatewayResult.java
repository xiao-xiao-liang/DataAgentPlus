package com.liang.data.agent.gateway.api;

/**
 * 模型网关阻塞调用结果，汇总内容、用量、路由与结束原因。
 *
 * @param invocationId 调用标识
 * @param content 完整响应内容
 * @param usage Token 使用量
 * @param route 最终路由信息
 * @param finishReason 结束原因
 */
public record GatewayResult(String invocationId, String content, ModelUsage usage,
                            ModelRoute route, String finishReason) {
}
