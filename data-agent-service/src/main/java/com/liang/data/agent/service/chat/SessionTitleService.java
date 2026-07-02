package com.liang.data.agent.service.chat;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.gateway.constants.ModelGatewayConstant;
import com.liang.data.agent.service.chat.vo.ChatSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步生成会话标题服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTitleService {

    private static final String DEFAULT_TITLE = "新会话";

    private final ChatSessionService chatSessionService;
    private final SessionEventPublisher sessionEventPublisher;
    private final LlmService llmService;

    /**
     * 正在进行标题生成的任务集合，防止并发重复执行
     */
    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

    /**
     * 异步调度会话标题生成
     *
     * @param sessionId 会话ID
     * @param userMessage 用户的首条提问内容
     */
    @Async
    public void scheduleTitleGeneration(String sessionId, String userMessage) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(userMessage)) {
            return;
        }
        if (!runningTasks.add(sessionId)) {
            return;
        }
        try {
            generateAndPersist(sessionId, userMessage);
        } finally {
            runningTasks.remove(sessionId);
        }
    }

    private void generateAndPersist(String sessionId, String userMessage) {
        try {
            ChatSessionVO session = chatSessionService.findBySessionId(sessionId);
            if (session == null) {
                log.warn("Session {} not found when generating title", sessionId);
                return;
            }
            
            // 已经是自定义标题（即不是“新会话”），则跳过生成
            if (hasCustomTitle(session)) {
                log.debug("Session {} already has custom title, skip generating", sessionId);
                return;
            }

            // 调用大模型进行总结
            String title = requestSummary(userMessage);
            if (!StringUtils.hasText(title)) {
                title = fallbackTitle(userMessage);
            }
            
            title = normalizeTitle(title);
            if (!StringUtils.hasText(title)) {
                log.warn("LLM returned empty title for session {}", sessionId);
                return;
            }

            // 保存标题到数据库
            chatSessionService.renameSession(sessionId, title);
            
            // 广播给前端
            sessionEventPublisher.publishTitleUpdated(session.getAgentId(), sessionId, title);
            log.info("Successfully generated and updated title '{}' for session {}", title, sessionId);

        } catch (Exception ex) {
            log.error("Failed to generate session title for session {}: {}", sessionId, ex.getMessage(), ex);
        }
    }

    private boolean hasCustomTitle(ChatSessionVO session) {
        return StringUtils.hasText(session.getTitle()) && !DEFAULT_TITLE.equals(session.getTitle());
    }

    private String requestSummary(String userMessage) {
        try {
            String systemPrompt = """
                    你是一名对话助手，请根据用户的第一条输入生成不超过20个字的会话标题。
                    使用中文输出，避免使用标点或引号，仅保留核心主题。
                    """;
            String userPrompt = "用户输入：" + userMessage;
            
            // 引入超时机制保护，防止大模型接口响应过慢
            return llmService.toStringFlux(llmService.call(ModelGatewayConstant.SESSION_TITLE, systemPrompt, userPrompt))
                    .collect(StringBuilder::new, StringBuilder::append)
                    .map(StringBuilder::toString)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception ex) {
            log.warn("LLM title generation failed, fallback to manual extraction: {}", ex.getMessage());
            return null;
        }
    }

    private String normalizeTitle(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String sanitized = raw.replaceAll("[\\r\\n]+", " ").replaceAll("[\"“”]+", "").trim();
        if (sanitized.length() > 20) {
            sanitized = sanitized.substring(0, 20);
        }
        return sanitized;
    }

    private String fallbackTitle(String userMessage) {
        String text = userMessage.replaceAll("\\s+", " ").trim();
        if (text.length() > 20) {
            text = text.substring(0, 20);
        }
        return StringUtils.hasText(text) ? text : DEFAULT_TITLE;
    }
}
