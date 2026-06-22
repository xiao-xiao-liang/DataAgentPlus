package com.liang.data.agent.gateway.api;

/**
 * 模型网关流式调用片段，描述增量内容及调用完成状态。
 *
 * @param invocationId 调用标识
 * @param content 增量响应内容
 * @param finished 是否已结束
 * @param usage Token 使用量
 * @param route 最终路由信息
 * @param finishReason 结束原因
 */
public record GatewayChunk(String invocationId, String content, boolean finished,
                           ModelUsage usage, ModelRoute route, String finishReason) {
}
