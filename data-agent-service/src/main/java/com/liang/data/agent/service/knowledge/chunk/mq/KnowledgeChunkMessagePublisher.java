package com.liang.data.agent.service.knowledge.chunk.mq;

import com.liang.data.agent.service.knowledge.chunk.KnowledgeChunkAsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 基于 RocketMQ 的知识分块异步任务发布器。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeChunkMessagePublisher implements KnowledgeChunkAsyncPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public boolean publishVectorizeTransaction(KnowledgeChunkTransactionOperation operation) {
        KnowledgeChunkMessage message = new KnowledgeChunkMessage(
                operation.agentId(), operation.knowledgeId(), operation.chunkId(),
                operation.resultContentVersion(), operation.resultTaskVersion(), UUID.randomUUID().toString());
        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
                KnowledgeChunkMqConstant.VECTORIZE_DESTINATION,
                MessageBuilder.withPayload(message)
                        .setHeader(KnowledgeChunkMqConstant.HEADER_CHUNK_ID, message.chunkId())
                        .setHeader(KnowledgeChunkMqConstant.HEADER_CONTENT_VERSION, message.contentVersion())
                        .setHeader(KnowledgeChunkMqConstant.HEADER_TASK_VERSION, message.taskVersion())
                        .build(),
                operation);
        return result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE;
    }

    @Override
    public boolean publishVectorize(Integer agentId, Integer knowledgeId, String chunkId,
                                    Integer contentVersion, Integer taskVersion) {
        rocketMQTemplate.convertAndSend(KnowledgeChunkMqConstant.VECTORIZE_DESTINATION,
                new KnowledgeChunkMessage(agentId, knowledgeId, chunkId, contentVersion,
                        taskVersion, UUID.randomUUID().toString()));
        return true;
    }

    @Override
    public boolean publishGenerateName(Integer agentId, Integer knowledgeId, String chunkId, Integer contentVersion) {
        rocketMQTemplate.convertAndSend(KnowledgeChunkMqConstant.GENERATE_NAME_DESTINATION,
                new KnowledgeChunkMessage(agentId, knowledgeId, chunkId, contentVersion, null, UUID.randomUUID().toString()));
        return true;
    }
}
