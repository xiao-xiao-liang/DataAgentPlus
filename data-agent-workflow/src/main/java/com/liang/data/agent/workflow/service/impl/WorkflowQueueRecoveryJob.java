package com.liang.data.agent.workflow.service.impl;

import static com.liang.data.agent.workflow.constants.WorkflowQueueConstants.QUEUE_SCOPE_CHAT_WORKFLOW;

import com.liang.data.agent.dal.entity.ChatWorkflowQueueEntity;
import com.liang.data.agent.dal.mapper.ChatWorkflowQueueMapper;
import com.liang.data.agent.workflow.config.WorkflowQueueProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 分析任务 Redis 队列恢复任务。
 *
 * <p>定时以 DB 中 WAITING/RUNNING 状态为准，补偿 Redis 等待队列和运行计数。</p>
 */
@Slf4j
@Component
@ConditionalOnBean(RedisWorkflowQueueScheduler.class)
@ConditionalOnProperty(name = "chat.workflow.queue.redis.enabled", havingValue = "true")
public class WorkflowQueueRecoveryJob {

    private final ChatWorkflowQueueMapper chatWorkflowQueueMapper;
    private final RedisWorkflowQueueScheduler redisWorkflowQueueScheduler;
    private final int recoveryBatchSize;

    public WorkflowQueueRecoveryJob(ChatWorkflowQueueMapper chatWorkflowQueueMapper,
                                    RedisWorkflowQueueScheduler redisWorkflowQueueScheduler,
                                    WorkflowQueueProperties workflowQueueProperties) {
        this.chatWorkflowQueueMapper = chatWorkflowQueueMapper;
        this.redisWorkflowQueueScheduler = redisWorkflowQueueScheduler;
        this.recoveryBatchSize = workflowQueueProperties.getRedis().getRecoveryBatchSize();
    }

    /**
     * 恢复 Redis 分析队列状态。
     */
    @Scheduled(fixedDelayString = "#{@workflowQueueProperties.redis.recoveryInterval.toMillis()}")
    public void recover() {
        try {
            // 1. 从 DB 查询等待和运行中的分析队列任务，DB 是最终状态来源。
            List<ChatWorkflowQueueEntity> waitingQueues = chatWorkflowQueueMapper.selectWaitingByScope(
                    QUEUE_SCOPE_CHAT_WORKFLOW,
                    recoveryBatchSize
            );
            List<ChatWorkflowQueueEntity> runningQueues = chatWorkflowQueueMapper.selectRunningByScope(
                    QUEUE_SCOPE_CHAT_WORKFLOW,
                    recoveryBatchSize
            );

            // 2. 将 DB 状态补偿到 Redis，修复 Redis 丢失或运行计数漂移。
            redisWorkflowQueueScheduler.recover(waitingQueues, runningQueues);
        } catch (Exception ex) {
            log.warn("恢复 Redis 分析队列状态失败", ex);
        }
    }
}
