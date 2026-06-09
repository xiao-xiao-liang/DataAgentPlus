package com.liang.data.agent.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.dal.entity.ChatWorkflowRunEntity;
import com.liang.data.agent.dal.mapper.ChatWorkflowRunMapper;
import com.liang.data.agent.workflow.service.WorkflowRunService;
import com.liang.data.agent.workflow.vo.WorkflowRunVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 工作流运行状态服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRunServiceImpl implements WorkflowRunService {

    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_INTERRUPTED = "interrupted";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";

    private final ChatWorkflowRunMapper chatWorkflowRunMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void startRun(String sessionId, Integer agentId, String userId, String query) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        ChatWorkflowRunEntity entity = ChatWorkflowRunEntity.builder()
                .sessionId(sessionId)
                .agentId(agentId)
                .userId(StringUtils.hasText(userId) ? userId.trim() : "default-user")
                .query(query)
                .status(STATUS_RUNNING)
                .build();
        chatWorkflowRunMapper.insert(entity);
        log.info("创建工作流运行记录，会话ID: {}, 运行ID: {}", sessionId, entity.getId());
    }

    @Override
    public void markNodeCompleted(String sessionId, String nodeName, String nextNodeName, String checkpointId,
                                  Map<String, Object> stateSnapshot, String accumulatedContent) {
        ChatWorkflowRunEntity latest = findLatestEntity(sessionId);
        if (latest == null) {
            return;
        }
        String snapshotJson = toJson(stateSnapshot);
        LambdaUpdateWrapper<ChatWorkflowRunEntity> wrapper = new LambdaUpdateWrapper<ChatWorkflowRunEntity>()
                .eq(ChatWorkflowRunEntity::getId, latest.getId())
                .set(ChatWorkflowRunEntity::getStatus, STATUS_RUNNING)
                .set(ChatWorkflowRunEntity::getLastNodeName, nodeName)
                .set(ChatWorkflowRunEntity::getNextNodeName, nextNodeName)
                .set(ChatWorkflowRunEntity::getCheckpointId, checkpointId)
                .set(ChatWorkflowRunEntity::getStateSnapshot, snapshotJson)
                .set(ChatWorkflowRunEntity::getAccumulatedContent, accumulatedContent);
        chatWorkflowRunMapper.update(null, wrapper);
        log.info("保存工作流节点快照，会话ID: {}, 节点: {}, 下一节点: {}", sessionId, nodeName, nextNodeName);
    }

    @Override
    public void markCompleted(String sessionId) {
        updateStatus(sessionId, STATUS_COMPLETED, null);
    }

    @Override
    public void markInterrupted(String sessionId, String reason) {
        updateStatus(sessionId, STATUS_INTERRUPTED, reason);
    }

    @Override
    public void markFailed(String sessionId, String reason) {
        updateStatus(sessionId, STATUS_FAILED, reason);
    }

    @Override
    public WorkflowRunVO findLatest(String sessionId) {
        return convertToVO(findLatestEntity(sessionId));
    }

    private void updateStatus(String sessionId, String status, String reason) {
        ChatWorkflowRunEntity latest = findLatestEntity(sessionId);
        if (latest == null) {
            return;
        }
        LambdaUpdateWrapper<ChatWorkflowRunEntity> wrapper = new LambdaUpdateWrapper<ChatWorkflowRunEntity>()
                .eq(ChatWorkflowRunEntity::getId, latest.getId())
                .set(ChatWorkflowRunEntity::getStatus, status)
                .set(ChatWorkflowRunEntity::getInterruptReason, reason);
        chatWorkflowRunMapper.update(null, wrapper);
        log.info("更新工作流运行状态，会话ID: {}, 状态: {}", sessionId, status);
    }

    private ChatWorkflowRunEntity findLatestEntity(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        LambdaQueryWrapper<ChatWorkflowRunEntity> wrapper = new LambdaQueryWrapper<ChatWorkflowRunEntity>()
                .eq(ChatWorkflowRunEntity::getSessionId, sessionId)
                .orderByDesc(ChatWorkflowRunEntity::getId)
                .last("LIMIT 1");
        return chatWorkflowRunMapper.selectOne(wrapper);
    }

    private WorkflowRunVO convertToVO(ChatWorkflowRunEntity entity) {
        if (entity == null) {
            return null;
        }
        boolean resumable = STATUS_INTERRUPTED.equals(entity.getStatus())
                && StringUtils.hasText(entity.getCheckpointId());
        return WorkflowRunVO.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .agentId(entity.getAgentId())
                .userId(entity.getUserId())
                .query(entity.getQuery())
                .status(entity.getStatus())
                .lastNodeName(entity.getLastNodeName())
                .nextNodeName(entity.getNextNodeName())
                .resumable(resumable)
                .interruptReason(entity.getInterruptReason())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private String toJson(Map<String, Object> stateSnapshot) {
        if (stateSnapshot == null || stateSnapshot.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(stateSnapshot);
        } catch (JsonProcessingException e) {
            log.warn("序列化工作流状态快照失败: {}", e.getMessage());
            return "{}";
        }
    }
}
