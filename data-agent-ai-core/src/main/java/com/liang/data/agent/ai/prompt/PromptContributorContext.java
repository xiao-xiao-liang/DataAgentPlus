package com.liang.data.agent.ai.prompt;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prompt 贡献者上下文
 *
 * <p>提供 PromptContributor 所需的上下文信息, 设计为与具体框架无关的抽象</p>
 */
public interface PromptContributorContext {

    /**
     * 获取消息列表
     */
    List<Message> getMessages();

    /**
     * 获取当前 System Message
     */
    Optional<SystemMessage> getSystemMessage();

    /**
     * 获取扩展属性 (任意自定义数据, 如 agentId、会话信息等)
     */
    Map<String, Object> getAttributes();

    /**
     * 便捷方法: 获取指定类型的属性值
     */
    default <T> Optional<T> getAttribute(String key, Class<T> type) {
        Object value = getAttributes().get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    /**
     * 获取当前阶段标识 (如 "NL2SQL"、"REPORT" 等), 可用于条件判断
     */
    Optional<String> getPhase();
}
