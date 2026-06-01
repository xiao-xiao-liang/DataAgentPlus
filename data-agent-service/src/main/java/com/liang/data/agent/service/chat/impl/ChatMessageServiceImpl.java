package com.liang.data.agent.service.chat.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.dal.entity.ChatMessageEntity;
import com.liang.data.agent.dal.mapper.ChatMessageMapper;
import com.liang.data.agent.service.chat.ChatMessageService;
import com.liang.data.agent.service.chat.dto.ChatMessageDTO;
import com.liang.data.agent.service.chat.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天消息服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<ChatMessageVO> findBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getCreateTime);
        List<ChatMessageEntity> entities = chatMessageMapper.selectList(wrapper);
        return entities.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO saveMessage(ChatMessageDTO dto, String sessionId) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(dto.getRole());
        entity.setContent(dto.getContent());
        entity.setMessageType(dto.getMessageType());
        entity.setMetadata(dto.getMetadata());

        chatMessageMapper.insert(entity);
        log.info("Saved message: {} for session: {}", entity.getId(), sessionId);
        return convertToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO saveOrUpdateStreamingAssistantMessage(String sessionId, String content, String messageType, boolean complete) {
        ChatMessageEntity latestStreamingMessage = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .eq(ChatMessageEntity::getRole, "assistant")
                        .eq(ChatMessageEntity::getMessageType, "streaming")
                        .orderByDesc(ChatMessageEntity::getId)
                        .last("LIMIT 1")
        );

        String nextMessageType = complete ? "text" : messageType;
        if (latestStreamingMessage == null) {
            ChatMessageDTO dto = ChatMessageDTO.builder()
                    .role("assistant")
                    .content(content)
                    .messageType(nextMessageType)
                    .build();
            return saveMessage(dto, sessionId);
        }

        LambdaUpdateWrapper<ChatMessageEntity> wrapper = new LambdaUpdateWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getId, latestStreamingMessage.getId())
                .set(ChatMessageEntity::getContent, content)
                .set(ChatMessageEntity::getMessageType, nextMessageType);
        chatMessageMapper.update(null, wrapper);
        latestStreamingMessage.setContent(content);
        latestStreamingMessage.setMessageType(nextMessageType);
        log.info("更新流式消息: {} for session: {}", latestStreamingMessage.getId(), sessionId);
        return convertToVO(latestStreamingMessage);
    }

    @Override
    @org.springframework.scheduling.annotation.Async
    @Transactional(rollbackFor = Exception.class)
    public void saveMessageAsync(ChatMessageDTO dto, String sessionId) {
        saveMessage(dto, sessionId);
    }

    @Override
    public String getMultiTurnContext(String sessionId, int limit) {
        // 获取最近 limit 条消息（按时间降序查，再反转为升序）
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByDesc(ChatMessageEntity::getId)
                .last("LIMIT " + limit);
        List<ChatMessageEntity> descEntities = chatMessageMapper.selectList(wrapper);
        if (descEntities.isEmpty()) {
            return "(无)";
        }

        List<ChatMessageEntity> entities = new ArrayList<>(descEntities);
        Collections.reverse(entities);

        List<String> turns = new ArrayList<>();
        String tempUser = null;

        for (ChatMessageEntity entity : entities) {
            if ("user".equals(entity.getRole())) {
                tempUser = entity.getContent();
            } else if ("assistant".equals(entity.getRole()) && tempUser != null) {
                String plan = extractPlanContent(entity.getContent());
                turns.add("用户: " + tempUser + "\nAI计划: " + plan);
                tempUser = null;
            }
        }

        if (turns.isEmpty()) {
            return "(无)";
        }
        return String.join("\n", turns);
    }

    /**
     * 辅助方法：从 AI 复杂的消息内容中提炼出执行计划，精简上下文
     */
    private String extractPlanContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        try {
            // 尝试寻找 $$$json ... $$$ 的标记
            int jsonStart = content.indexOf("$$$json");
            if (jsonStart != -1) {
                int startOfContent = jsonStart + "$$$json".length();
                int jsonEnd = content.indexOf("$$$", startOfContent);
                if (jsonEnd != -1) {
                    String jsonStr = content.substring(startOfContent, jsonEnd).trim();
                    JsonNode node = objectMapper.readTree(jsonStr);
                    // 提取思维过程或执行步骤
                    if (node.has("thought_process")) {
                        return node.get("thought_process").asText();
                    } else if (node.has("execution_plan")) {
                        return node.get("execution_plan").toString();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract plan from assistant message JSON: {}", e.getMessage());
        }

        // 如果找不到或解析失败，截断内容作为提炼
        String sanitized = content.replace("$$$json", "")
                .replace("$$$sql", "")
                .replace("$$$result_set", "")
                .replace("$$$markdown-report", "")
                .replace("$$$/markdown-report", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.length() > 300) {
            return sanitized.substring(0, 300) + "...";
        }
        return sanitized;
    }

    private ChatMessageVO convertToVO(ChatMessageEntity entity) {
        if (entity == null) {
            return null;
        }
        return ChatMessageVO.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .role(entity.getRole())
                .content(entity.getContent())
                .messageType(entity.getMessageType())
                .metadata(entity.getMetadata())
                .createTime(entity.getCreateTime())
                .build();
    }
}
