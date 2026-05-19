package com.liang.data.agent.ai.util;

import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Optional;

/**
 * ChatResponse 工具类
 */
@NoArgsConstructor
public final class ChatResponseUtil {

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
     * 创建带换行的 ChatResponse (用于用户可见的状态消息)
     *
     * <p>示例: createResponse("正在生成SQL...") → 内容为 "正在生成SQL...\n"</p>
     */
    public static ChatResponse createResponse(String statusMessage) {
        return createPureResponse(statusMessage + "\n");
    }

    /**
     * 创建不加换行的 ChatResponse (用于标记符、代码片段等不需要额外换行的场景)
     *
     * <p>示例: createPureResponse(TextType.SQL.getStartSign()) → 内容为 "$$$sql"</p>
     */
    public static ChatResponse createPureResponse(String message) {
        AssistantMessage assistantMessage = new AssistantMessage(message);
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }
}
