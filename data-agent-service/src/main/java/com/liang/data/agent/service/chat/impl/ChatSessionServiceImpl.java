package com.liang.data.agent.service.chat.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.liang.data.agent.dal.entity.ChatMessageEntity;
import com.liang.data.agent.dal.entity.ChatSessionEntity;
import com.liang.data.agent.dal.mapper.ChatMessageMapper;
import com.liang.data.agent.dal.mapper.ChatSessionMapper;
import com.liang.data.agent.service.chat.ChatSessionService;
import com.liang.data.agent.service.chat.vo.ChatSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天会话服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    @Override
    public List<ChatSessionVO> findByAgentId(Integer agentId) {
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getAgentId, agentId)
                .orderByDesc(ChatSessionEntity::getIsPinned)
                .orderByDesc(ChatSessionEntity::getUpdateTime);
        List<ChatSessionEntity> entities = chatSessionMapper.selectList(wrapper);
        return entities.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO createSession(Integer agentId, String title, Long userId) {
        String sessionId = UUID.randomUUID().toString();
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(sessionId);
        entity.setAgentId(agentId);
        entity.setTitle(Optional.ofNullable(title).orElse("新会话"));
        entity.setStatus("active");
        entity.setIsPinned(0);
        entity.setUserId(userId);
        chatSessionMapper.insert(entity);
        log.info("Created new chat session: {} for agent: {}", sessionId, agentId);
        return convertToVO(entity);
    }

    @Override
    public ChatSessionVO findBySessionId(String sessionId) {
        ChatSessionEntity entity = chatSessionMapper.selectById(sessionId);
        return Optional.ofNullable(entity).map(this::convertToVO).orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearSessionsByAgentId(Integer agentId) {
        // 先查询出该 Agent 下所有的 Session ID，用于后续清理其消息
        LambdaQueryWrapper<ChatSessionEntity> queryWrapper = new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getAgentId, agentId);
        List<ChatSessionEntity> sessions = chatSessionMapper.selectList(queryWrapper);
        if (sessions.isEmpty()) {
            return;
        }

        List<String> sessionIds = sessions.stream().map(ChatSessionEntity::getId).collect(Collectors.toList());

        // 逻辑删除会话
        LambdaUpdateWrapper<ChatSessionEntity> updateWrapper = new LambdaUpdateWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getAgentId, agentId)
                .set(ChatSessionEntity::getDelFlag, 1)
                .set(ChatSessionEntity::getUpdateTime, LocalDateTime.now());
        int updated = chatSessionMapper.update(null, updateWrapper);

        // 物理清理所有这些会话下的消息，防止脏数据堆积
        LambdaQueryWrapper<ChatMessageEntity> messageWrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .in(ChatMessageEntity::getSessionId, sessionIds);
        chatMessageMapper.delete(messageWrapper);

        log.info("Cleared {} sessions and their messages for agent: {}", updated, agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionTime(String sessionId) {
        LambdaUpdateWrapper<ChatSessionEntity> wrapper = new LambdaUpdateWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, sessionId)
                .set(ChatSessionEntity::getUpdateTime, LocalDateTime.now());
        chatSessionMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinSession(String sessionId, boolean isPinned) {
        LambdaUpdateWrapper<ChatSessionEntity> wrapper = new LambdaUpdateWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, sessionId)
                .set(ChatSessionEntity::getIsPinned, isPinned ? 1 : 0)
                .set(ChatSessionEntity::getUpdateTime, LocalDateTime.now());
        chatSessionMapper.update(null, wrapper);
        log.info("Updated pin status for session: {} to: {}", sessionId, isPinned);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameSession(String sessionId, String newTitle) {
        LambdaUpdateWrapper<ChatSessionEntity> wrapper = new LambdaUpdateWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, sessionId)
                .set(ChatSessionEntity::getTitle, newTitle)
                .set(ChatSessionEntity::getUpdateTime, LocalDateTime.now());
        chatSessionMapper.update(null, wrapper);
        log.info("Renamed session: {} to: {}", sessionId, newTitle);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        // 逻辑删除会话
        chatSessionMapper.deleteById(sessionId);

        // 物理删除消息
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId);
        chatMessageMapper.delete(wrapper);
        log.info("Deleted session: {} and its messages", sessionId);
    }

    private ChatSessionVO convertToVO(ChatSessionEntity entity) {
        if (entity == null) {
            return null;
        }
        return ChatSessionVO.builder()
                .id(entity.getId())
                .agentId(entity.getAgentId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .isPinned(entity.getIsPinned())
                .userId(entity.getUserId())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
