package com.liang.data.agent.gateway.response;

import java.util.Objects;

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

    public GatewayChunk {
        // 1. 校验片段基础字段，所有片段都必须能关联调用并携带非 null 内容。
        if (invocationId == null || invocationId.isBlank()) {
            throw new IllegalArgumentException("调用标识不能为空");
        }
        Objects.requireNonNull(content, "响应内容不能为空");
        // 2. 完成片段必须携带最终统计、路由和结束原因。
        if (finished) {
            Objects.requireNonNull(usage, "使用量不能为空");
            Objects.requireNonNull(route, "路由不能为空");
            if (finishReason == null || finishReason.isBlank()) {
                throw new IllegalArgumentException("结束原因不能为空");
            }
        }
    }
}
