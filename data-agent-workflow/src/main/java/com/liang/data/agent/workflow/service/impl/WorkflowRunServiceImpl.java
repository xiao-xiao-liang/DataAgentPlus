package com.liang.data.agent.workflow.service.impl;

import static com.liang.data.agent.workflow.constants.WorkflowRunConstants.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.dal.entity.ChatWorkflowRunEntity;
import com.liang.data.agent.dal.mapper.ChatWorkflowRunMapper;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.workflow.service.WorkflowRunService;
import com.liang.data.agent.workflow.vo.WorkflowRunVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作流运行状态服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRunServiceImpl implements WorkflowRunService {

    private final ChatWorkflowRunMapper chatWorkflowRunMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void startRun(String sessionId, Integer agentId, Long userId, String query) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        LocalDateTime startTime = LocalDateTime.now();
        ChatWorkflowRunEntity entity = ChatWorkflowRunEntity.builder()
                .sessionId(sessionId)
                .agentId(agentId)
                .userId(userId != null ? userId : 1L)
                .query(query)
                .status(STATUS_RUNNING)
                .startTime(startTime)
                .build();
        chatWorkflowRunMapper.insert(entity);
        log.info("创建工作流运行记录，会话ID: {}, 运行ID: {}", sessionId, entity.getId());
    }

    @Override
    public void startRun(GatewayExecutionContext context, String query) {
        if (context == null || !StringUtils.hasText(context.runId())) {
            return;
        }
        // 1. 生成统一开始时间，作为后续耗时计算的基准。
        LocalDateTime startTime = LocalDateTime.now();
        // 2. 将模型网关上下文中的运行身份和追踪身份写入运行记录。
        ChatWorkflowRunEntity entity = ChatWorkflowRunEntity.builder()
                .runId(context.runId())
                .traceId(context.traceId())
                .sessionId(context.sessionId())
                .agentId(context.agentId())
                .userId(context.userId() != null ? context.userId() : 1L)
                .query(query)
                .status(STATUS_RUNNING)
                .startTime(startTime)
                .build();
        chatWorkflowRunMapper.insert(entity);
        log.info("创建工作流运行记录，运行ID: {}, 追踪ID: {}", context.runId(), context.traceId());
    }

    @Override
    public void markNodeCompleted(String runId, String nodeName, String nextNodeName, String checkpointId,
                                  Map<String, Object> stateSnapshot, String accumulatedContent) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        // 1. 序列化节点状态快照，避免在日志中记录完整响应内容。
        String snapshotJson = toJson(stateSnapshot);
        // 2. 使用运行ID定位单次工作流运行记录并更新节点进度。
        LambdaUpdateWrapper<ChatWorkflowRunEntity> wrapper = new LambdaUpdateWrapper<ChatWorkflowRunEntity>()
                .eq(ChatWorkflowRunEntity::getRunId, runId)
                .set(ChatWorkflowRunEntity::getStatus, STATUS_RUNNING)
                .set(ChatWorkflowRunEntity::getLastNodeName, nodeName)
                .set(ChatWorkflowRunEntity::getNextNodeName, nextNodeName)
                .set(ChatWorkflowRunEntity::getCheckpointId, checkpointId)
                .set(ChatWorkflowRunEntity::getStateSnapshot, snapshotJson)
                .set(ChatWorkflowRunEntity::getAccumulatedContent, accumulatedContent);
        chatWorkflowRunMapper.update(null, wrapper);
        log.info("保存工作流节点快照，运行ID: {}, 节点: {}, 下一节点: {}", runId, nodeName, nextNodeName);
    }

    @Override
    public void markCompleted(String runId) {
        updateStatus(runId, STATUS_COMPLETED, null, null);
    }

    @Override
    public void markInterrupted(String runId, String reason) {
        updateStatus(runId, STATUS_INTERRUPTED, null, reason);
    }

    @Override
    public void markFailed(String runId, String failedNodeName, String reason) {
        updateStatus(runId, STATUS_FAILED, failedNodeName, reason);
    }

    @Override
    public WorkflowRunVO findLatest(String sessionId) {
        return convertToVO(findLatestEntity(sessionId));
    }

    private void updateStatus(String runId, String status, String failedNodeName, String reason) {
        ChatWorkflowRunEntity entity = findByRunId(runId);
        if (entity == null) {
            return;
        }
        // 1. 使用实体开始时间计算耗时，避免混用毫秒时间戳与数据库时间。
        LocalDateTime endTime = LocalDateTime.now();
        Long durationMs = calculateDurationMs(entity.getStartTime(), endTime);
        // 2. 按运行ID更新最终状态、结束时间和耗时。
        LambdaUpdateWrapper<ChatWorkflowRunEntity> wrapper = new LambdaUpdateWrapper<ChatWorkflowRunEntity>()
                .eq(ChatWorkflowRunEntity::getRunId, runId)
                .set(ChatWorkflowRunEntity::getStatus, status)
                .set(ChatWorkflowRunEntity::getInterruptReason, reason)
                .set(ChatWorkflowRunEntity::getEndTime, endTime)
                .set(ChatWorkflowRunEntity::getDurationMs, durationMs);
        if (STATUS_FAILED.equals(status)) {
            wrapper.set(ChatWorkflowRunEntity::getFailedNodeName, failedNodeName);
        }
        chatWorkflowRunMapper.update(null, wrapper);
        log.info("更新工作流运行状态，运行ID: {}, 状态: {}", runId, status);
    }

    private ChatWorkflowRunEntity findByRunId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return null;
        }
        LambdaQueryWrapper<ChatWorkflowRunEntity> wrapper = new LambdaQueryWrapper<ChatWorkflowRunEntity>()
                .eq(ChatWorkflowRunEntity::getRunId, runId)
                .last("LIMIT 1");
        return chatWorkflowRunMapper.selectOne(wrapper);
    }

    private Long calculateDurationMs(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return Duration.between(startTime, endTime).toMillis();
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
                .runId(entity.getRunId())
                .traceId(entity.getTraceId())
                .sessionId(entity.getSessionId())
                .agentId(entity.getAgentId())
                .userId(entity.getUserId())
                .query(entity.getQuery())
                .status(entity.getStatus())
                .lastNodeName(entity.getLastNodeName())
                .nextNodeName(entity.getNextNodeName())
                .resumable(resumable)
                .interruptReason(entity.getInterruptReason())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .durationMs(entity.getDurationMs())
                .failedNodeName(entity.getFailedNodeName())
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
