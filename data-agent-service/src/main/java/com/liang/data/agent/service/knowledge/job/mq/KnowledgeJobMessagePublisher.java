package com.liang.data.agent.service.knowledge.job.mq;

import com.liang.data.agent.service.knowledge.job.KnowledgeJobAsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 基于 RocketMQ 的知识文档任务发布器。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeJobMessagePublisher implements KnowledgeJobAsyncPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void publish(Long jobId) {
        if (jobId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(jobId);
                }
            });
            return;
        }
        doPublish(jobId);
    }

    private void doPublish(Long jobId) {
        KnowledgeJobMessage message = new KnowledgeJobMessage(jobId, UUID.randomUUID().toString());
        rocketMQTemplate.send(KnowledgeJobMqConstant.EXECUTE_DESTINATION,
                MessageBuilder.withPayload(message).build());
    }
}
