package com.liang.data.agent.workflow.service.impl;

import com.liang.data.agent.dal.entity.ChatWorkflowQueueEntity;
import com.liang.data.agent.dal.mapper.ChatWorkflowQueueMapper;
import com.liang.data.agent.workflow.config.WorkflowQueueProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分析任务 Redis 队列恢复任务单元测试。
 */
class WorkflowQueueRecoveryJobTest {

    private final ChatWorkflowQueueMapper chatWorkflowQueueMapper = mock(ChatWorkflowQueueMapper.class);
    private final RedisWorkflowQueueScheduler redisWorkflowQueueScheduler = mock(RedisWorkflowQueueScheduler.class);
    private final WorkflowQueueProperties workflowQueueProperties = workflowQueueProperties();
    private final WorkflowQueueRecoveryJob recoveryJob = new WorkflowQueueRecoveryJob(
            chatWorkflowQueueMapper,
            redisWorkflowQueueScheduler,
            workflowQueueProperties
    );

    @Test
    void recoverShouldUseDatabaseStateToRebuildRedisQueue() {
        List<ChatWorkflowQueueEntity> waitingQueues = List.of(waitingQueue("queue-1", 1001L, 1L));
        List<ChatWorkflowQueueEntity> runningQueues = List.of(runningQueue("queue-2", 1002L, 2L));
        when(chatWorkflowQueueMapper.selectWaitingByScope("CHAT_WORKFLOW", 50)).thenReturn(waitingQueues);
        when(chatWorkflowQueueMapper.selectRunningByScope("CHAT_WORKFLOW", 50)).thenReturn(runningQueues);

        recoveryJob.recover();

        verify(redisWorkflowQueueScheduler).recover(waitingQueues, runningQueues);
    }

    private ChatWorkflowQueueEntity waitingQueue(String queueId, Long userId, Long id) {
        return ChatWorkflowQueueEntity.builder()
                .id(id)
                .queueId(queueId)
                .userId(userId)
                .status("WAITING")
                .queueScope("CHAT_WORKFLOW")
                .queuedAt(LocalDateTime.now())
                .build();
    }

    private ChatWorkflowQueueEntity runningQueue(String queueId, Long userId, Long id) {
        ChatWorkflowQueueEntity entity = waitingQueue(queueId, userId, id);
        entity.setStatus("RUNNING");
        return entity;
    }

    private WorkflowQueueProperties workflowQueueProperties() {
        WorkflowQueueProperties properties = new WorkflowQueueProperties();
        properties.getRedis().setRecoveryBatchSize(50);
        return properties;
    }
}
