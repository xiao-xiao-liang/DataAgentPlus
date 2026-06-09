package com.liang.data.agent.workflow.service.impl;

import com.liang.data.agent.dal.entity.ChatWorkflowQueueEntity;
import com.liang.data.agent.dal.mapper.ChatWorkflowQueueMapper;
import com.liang.data.agent.workflow.enums.WorkflowQueueStatus;
import com.liang.data.agent.workflow.service.WorkflowAdmissionService;
import com.liang.data.agent.workflow.vo.WorkflowQueueVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 基于数据库的分析任务准入服务实现。
 */
@Slf4j
@Service
public class WorkflowAdmissionServiceImpl implements WorkflowAdmissionService {

    private static final String QUEUE_SCOPE_CHAT_WORKFLOW = "CHAT_WORKFLOW";

    private final ChatWorkflowQueueMapper chatWorkflowQueueMapper;
    private final int maxUserRunning;
    private final int maxGlobalRunning;

    public WorkflowAdmissionServiceImpl(ChatWorkflowQueueMapper chatWorkflowQueueMapper,
                                        @Value("${chat.workflow.queue.max-user-running:2}") int maxUserRunning,
                                        @Value("${chat.workflow.queue.max-global-running:10}") int maxGlobalRunning) {
        this.chatWorkflowQueueMapper = chatWorkflowQueueMapper;
        this.maxUserRunning = maxUserRunning;
        this.maxGlobalRunning = maxGlobalRunning;
    }

    @Override
    public WorkflowQueueVO enqueue(String userId, String sessionId, Integer agentId, String query) {
        LocalDateTime now = LocalDateTime.now();
        ChatWorkflowQueueEntity entity = ChatWorkflowQueueEntity.builder()
                .queueId(UUID.randomUUID().toString())
                .userId(normalizeUserId(userId))
                .sessionId(sessionId)
                .agentId(agentId)
                .query(query)
                .status(WorkflowQueueStatus.WAITING.name())
                .queueScope(QUEUE_SCOPE_CHAT_WORKFLOW)
                .queuedAt(now)
                .build();
        chatWorkflowQueueMapper.insert(entity);
        log.info("创建分析任务排队记录，会话ID：{}，队列ID：{}，用户ID：{}", sessionId, entity.getQueueId(), entity.getUserId());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowQueueVO tryPromote(String queueId) {
        ChatWorkflowQueueEntity entity = chatWorkflowQueueMapper.selectByQueueId(queueId);
        if (entity == null || !WorkflowQueueStatus.WAITING.name().equals(entity.getStatus())) {
            return toVO(entity);
        }

        // 1. 检查同用户运行中任务数，避免单用户占满分析资源。
        long userRunning = chatWorkflowQueueMapper.countRunningByUser(entity.getUserId(), entity.getQueueScope());
        if (userRunning >= maxUserRunning) {
            return toVO(entity, userRunning);
        }

        // 2. 检查全局运行中任务数，保护整体分析链路。
        long globalRunning = chatWorkflowQueueMapper.countRunningByScope(entity.getQueueScope());
        if (globalRunning >= maxGlobalRunning) {
            return toVO(entity, userRunning);
        }

        // 3. 只允许队首任务被推进，保证队列位次可解释。
        boolean hasEarlierWaiting = chatWorkflowQueueMapper.existsEarlierWaiting(
                entity.getQueueScope(),
                entity.getQueuedAt(),
                entity.getId()
        );
        if (hasEarlierWaiting) {
            return toVO(entity, userRunning);
        }

        int rows = chatWorkflowQueueMapper.markRunning(queueId);
        if (rows == 1) {
            entity.setStatus(WorkflowQueueStatus.RUNNING.name());
            entity.setStartedAt(LocalDateTime.now());
            log.info("分析任务获得运行资格，会话ID：{}，队列ID：{}", entity.getSessionId(), entity.getQueueId());
        }
        return toVO(entity, userRunning + rows);
    }

    @Override
    public WorkflowQueueVO queryPosition(String queueId) {
        return toVO(chatWorkflowQueueMapper.selectByQueueId(queueId));
    }

    @Override
    public void complete(String queueId) {
        markFinished(queueId, WorkflowQueueStatus.COMPLETED, null);
    }

    @Override
    public void fail(String queueId, String reason) {
        markFinished(queueId, WorkflowQueueStatus.FAILED, reason);
    }

    @Override
    public void cancel(String queueId, String reason) {
        markFinished(queueId, WorkflowQueueStatus.CANCELLED, reason);
    }

    private void markFinished(String queueId, WorkflowQueueStatus status, String reason) {
        if (!StringUtils.hasText(queueId)) {
            return;
        }
        chatWorkflowQueueMapper.markFinished(queueId, status.name(), reason);
        log.info("更新分析任务队列状态，队列ID：{}，状态：{}", queueId, status.name());
    }

    private WorkflowQueueVO toVO(ChatWorkflowQueueEntity entity) {
        if (entity == null) {
            return null;
        }
        long userRunning = chatWorkflowQueueMapper.countRunningByUser(entity.getUserId(), entity.getQueueScope());
        return toVO(entity, userRunning);
    }

    private WorkflowQueueVO toVO(ChatWorkflowQueueEntity entity, long userRunning) {
        if (entity == null) {
            return null;
        }
        long aheadTaskCount = 0;
        long aheadUserCount = 0;
        if (WorkflowQueueStatus.WAITING.name().equals(entity.getStatus())) {
            aheadTaskCount = chatWorkflowQueueMapper.countAheadTasks(entity.getQueueScope(), entity.getQueuedAt(), entity.getId());
            aheadUserCount = chatWorkflowQueueMapper.countAheadUsers(entity.getQueueScope(), entity.getQueuedAt(), entity.getId());
        }
        return WorkflowQueueVO.builder()
                .queueId(entity.getQueueId())
                .status(entity.getStatus())
                .aheadTaskCount(aheadTaskCount)
                .aheadUserCount(aheadUserCount)
                .runningTaskCount(userRunning)
                .maxUserRunningLimit(maxUserRunning)
                .build();
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim() : "default-user";
    }
}
