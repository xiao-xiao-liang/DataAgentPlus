package com.liang.data.agent.gateway.prompt;

import java.util.List;
import java.util.Objects;

/**
 * 已解析提示词。
 *
 * <p>保存模板注册中心解析后的模板标识、版本与模型消息快照，供模型网关后续调用使用。</p>
 *
 * @param templateId 模板标识
 * @param version 模板版本
 * @param messages 模型消息列表
 */
public record ResolvedPrompt(String templateId, String version, List<ModelMessage> messages) {

    public ResolvedPrompt {
        // 1. 校验模板元信息，确保调用链路可以追踪模板来源。
        templateId = requireNonBlank(templateId, "模板标识不能为空");
        version = requireNonBlank(version, "模板版本不能为空");

        // 2. 校验消息列表，确保解析结果可以直接用于模型调用。
        Objects.requireNonNull(messages, "提示词消息列表不能为空");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("提示词消息列表不能为空");
        }
        for (ModelMessage message : messages) {
            Objects.requireNonNull(message, "提示词消息不能为空");
        }

        // 3. 复制消息列表，避免外部集合变更影响解析后的提示词。
        messages = List.copyOf(messages);
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
