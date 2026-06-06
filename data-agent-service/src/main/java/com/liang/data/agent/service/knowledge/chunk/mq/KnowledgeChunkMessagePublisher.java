package com.liang.data.agent.service.knowledge.chunk.mq;

import com.liang.data.agent.service.knowledge.chunk.KnowledgeChunkAsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 RocketMQ 的知识分块异步任务发布器。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeChunkMessagePublisher implements KnowledgeChunkAsyncPublisher {

    public static final String TOPIC = "data-agent-knowledge-chunk";
    public static final String VECTORIZE_DESTINATION = TOPIC + ":VECTORIZE";
    public static final String GENERATE_NAME_DESTINATION = TOPIC + ":GENERATE_NAME";

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public boolean publishVectorize(Integer agentId, Integer knowledgeId, String chunkId, Integer contentVersion) {
        rocketMQTemplate.convertAndSend(VECTORIZE_DESTINATION,
                new KnowledgeChunkMessage(agentId, knowledgeId, chunkId, contentVersion));
        return true;
    }

    @Override
    public boolean publishGenerateName(Integer agentId, Integer knowledgeId, String chunkId, Integer contentVersion) {
        rocketMQTemplate.convertAndSend(GENERATE_NAME_DESTINATION,
                new KnowledgeChunkMessage(agentId, knowledgeId, chunkId, contentVersion));
        return true;
    }
}
