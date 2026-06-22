package com.liang.data.agent.gateway.api;

import java.util.List;
import java.util.Map;

/**
 * 模型提示词，支持模板引用与直接消息两种互斥模式。
 *
 * @param templateId 模板标识
 * @param variables 模板变量
 * @param messages 直接消息列表
 */
public record ModelPrompt(String templateId, Map<String, Object> variables, List<ModelMessage> messages) {

    public ModelPrompt {
        // 1. 复制外部集合，避免构造后的提示词被调用方修改。
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        messages = messages == null ? List.of() : List.copyOf(messages);
        // 2. 校验提示词来源，模板模式与直接消息模式必须二选一。
        boolean templateMode = templateId != null && !templateId.isBlank();
        if (templateMode == !messages.isEmpty()) {
            throw new IllegalArgumentException("模板标识与直接消息必须且只能提供一种");
        }
    }

    /**
     * 创建直接消息模式的提示词。
     *
     * @param messages 直接消息列表
     * @return 直接消息模式提示词
     */
    public static ModelPrompt direct(List<ModelMessage> messages) {
        return new ModelPrompt(null, Map.of(), messages);
    }

    /**
     * 创建模板模式的提示词。
     *
     * @param templateId 模板标识
     * @param variables 模板变量
     * @return 模板模式提示词
     */
    public static ModelPrompt template(String templateId, Map<String, Object> variables) {
        return new ModelPrompt(templateId, variables, List.of());
    }
}
