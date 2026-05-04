package com.liang.data.agent.ai.util;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Optional;

/**
 * ChatResponse 工具类
 */
public final class ChatResponseUtil {

    private ChatResponseUtil() {
    }

    /**
     * 安全提取 ChatResponse 中的文本内容
     *
     * @return 文本内容, 如果为空返回 ""
     */
    public static String getText(ChatResponse chatResponse) {
        return Optional.ofNullable(chatResponse.getResult())
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .orElse("");
    }

    /**
     * 创建简单的 ChatResponse (用于节点间传递状态消息)
     */
    public static ChatResponse createResponse(String message) {
        AssistantMessage assistantMessage = new AssistantMessage(message + "\n");
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }
}
