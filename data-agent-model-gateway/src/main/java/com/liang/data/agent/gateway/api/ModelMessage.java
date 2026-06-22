package com.liang.data.agent.gateway.api;

import java.util.Objects;

/**
 * 模型消息，封装消息角色与文本内容。
 *
 * @param role 消息角色
 * @param content 消息内容
 */
public record ModelMessage(ModelMessageRole role, String content) {

    public ModelMessage {
        // 1. 校验消息角色，确保调用方明确消息来源。
        Objects.requireNonNull(role, "消息角色不能为空");
        // 2. 校验消息内容，避免向模型发送空白消息。
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
    }
}
