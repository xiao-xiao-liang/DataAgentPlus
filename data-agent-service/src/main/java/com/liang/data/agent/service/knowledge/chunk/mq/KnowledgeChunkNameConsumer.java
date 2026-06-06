package com.liang.data.agent.service.knowledge.chunk.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.service.knowledge.chunk.ChunkNameGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 知识分块名称生成消息消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = KnowledgeChunkMessagePublisher.TOPIC,
        selectorExpression = "GENERATE_NAME",
        consumerGroup = "data-agent-knowledge-chunk-name-consumer")
public class KnowledgeChunkNameConsumer implements RocketMQListener<KnowledgeChunkMessage> {

    private final AgentKnowledgeChunkMapper chunkMapper;
    private final ChunkNameGenerator nameGenerator;

    @Override
    public void onMessage(KnowledgeChunkMessage message) {
        AgentKnowledgeChunkEntity chunk = chunkMapper.selectOne(Wrappers.lambdaQuery(AgentKnowledgeChunkEntity.class)
                .eq(AgentKnowledgeChunkEntity::getKnowledgeId, message.knowledgeId())
                .eq(AgentKnowledgeChunkEntity::getChunkId, message.chunkId()));
        if (chunk == null || !message.contentVersion().equals(chunk.getContentVersion())
                || Integer.valueOf(1).equals(chunk.getNameLocked())) {
            return;
        }
        String name = nameGenerator.generate(chunk.getContent(), chunk.getChunkOrder());
        if (chunkMapper.updateNameIfUnlocked(message.chunkId(), message.contentVersion(), name) == 0) {
            log.info("忽略晚到的分块名称生成结果：chunkId={}，contentVersion={}",
                    message.chunkId(), message.contentVersion());
        }
    }
}
