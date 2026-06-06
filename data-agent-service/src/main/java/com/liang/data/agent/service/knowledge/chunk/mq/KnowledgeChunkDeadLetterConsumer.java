package com.liang.data.agent.service.knowledge.chunk.mq;

import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 知识分块向量化死信消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = KnowledgeChunkMqConstant.VECTOR_DEAD_LETTER_TOPIC,
        consumerGroup = KnowledgeChunkMqConstant.VECTOR_DEAD_LETTER_CONSUMER_GROUP)
public class KnowledgeChunkDeadLetterConsumer implements RocketMQListener<KnowledgeChunkMessage> {

    private final AgentKnowledgeChunkMapper chunkMapper;

    @Override
    public void onMessage(KnowledgeChunkMessage message) {
        int rows = chunkMapper.markVectorFailedIfCurrent(
                message.chunkId(), message.contentVersion(), message.taskVersion(), "分块向量化重试耗尽，请手动重试");
        if (rows == 0) {
            log.warn("忽略已过期的分块向量化死信：chunkId={}，contentVersion={}",
                    message.chunkId(), message.contentVersion());
        }
    }
}
